# Работа с VictoriaMetrics в проекте ZK_tablecreater

Техническое описание интеграции с **VictoriaMetrics (VM)** для последующей разработки аналогичного приложения (анализатор метрик / построитель отчётов). Документ отражает фактическую реализацию в данном репозитории, а не «идеальный» дизайн.

Связанные материалы в репозитории:

- [`docs/metrics-accuracy.md`](docs/metrics-accuracy.md) — как добиться совпадения с Grafana
- [`victoriametrics-java-app-vs-grafana.md`](victoriametrics-java-app-vs-grafana.md) — почему числа в Java-приложении отличаются от Grafana/UI VM
- [`victoriametrics-query-range-step-datapoints.md`](victoriametrics-query-range-step-datapoints.md) — про `query_range` и лимит точек
- [`docs/pod-startup-time.md`](docs/pod-startup-time.md) — время старта подов
- [`prometheus-openshift-cpu-metrics.md`](prometheus-openshift-cpu-metrics.md) — CPU-метрики OpenShift/cAdvisor

---

## 1. Назначение интеграции

Приложение — **Spring Boot MVC** (`artifactId: ZKN`), которое:

1. Принимает HTTP-запрос (часто из **Grafana** iframe / link с `${__from}`, `${__to}`, `${namespace}` / `var-instance` / `var-server`).
2. Делает серию **Prometheus-compatible** запросов в VictoriaMetrics (`GET /api/v1/query`).
3. Склеивает сырые ряды в доменные DTO.
4. Отдаёт **HTML-таблицу** (строкой из Java), **JSON** или **Excel (XLSX)**.

VM используется только для **чтения**. Запись метрик, remote_write, vmalert и т.п. в проекте нет.

Поддерживаются **три независимых домена** поверх одного HTTP-клиента:

| Домен | Сервис | Источник метрик в VM | UI-эндпоинты |
|-------|--------|----------------------|--------------|
| Kubernetes / OpenShift workloads | `VictoriaMetricsService` | kube-state-metrics + cAdvisor | `/get`, `/getJson`, `/getHtml`, `/getExcel` |
| Linux hosts | `NodeExporterMetricsService` | node_exporter | `/servers`, `/serversJson` |
| PostgreSQL checks | `PostgresDbChecksMetricsService` | postgres_exporter-подобные ряды | `/dbChecks`, `/dbChecksJson` |

---

## 2. Архитектура (слои)

```
Клиент (браузер / Grafana)
        │
        ▼
MainController                    ← HTTP-параметры: from/to (мс), фильтры
        │
        ├─ VictoriaMetricsService
        ├─ NodeExporterMetricsService
        └─ PostgresDbChecksMetricsService
                │
                ▼
        VictoriaMetricsClient     ← RestTemplate → VM
                │
                ▼
        PrometheusResponse (Jackson)
                │
                ▼
        DTO (Deployment/Container | LinuxServerMetrics | PostgresQueryMetrics)
                │
                ▼
        Html*Service / ExcelTableService / raw JSON
```

**Принцип:** тонкий transport-клиент + толстые доменные сервисы с PromQL и бизнес-агрегацией.

---

## 3. Карта исходников

```
src/main/resources/application.properties
src/main/java/com/example/demo/Config/VictoriaMetricsConfig.java
src/main/java/com/example/demo/Services/VictoriaMetricsClient.java
src/main/java/com/example/demo/Services/VictoriaMetricsService.java
src/main/java/com/example/demo/Services/NodeExporterMetricsService.java
src/main/java/com/example/demo/Services/PostgresDbChecksMetricsService.java
src/main/java/com/example/demo/Controllers/MainController.java
src/main/java/com/example/demo/DTO/PrometheusResponse.java
src/main/java/com/example/demo/DTO/Deployment.java
src/main/java/com/example/demo/DTO/Container.java
src/main/java/com/example/demo/DTO/LinuxServerMetrics.java
src/main/java/com/example/demo/DTO/PostgresQueryMetrics.java
src/main/java/com/example/demo/Services/HtmlTableService.java
src/main/java/com/example/demo/Services/ExcelTableService.java
src/main/java/com/example/demo/Services/LinuxServersHtmlService.java
src/main/java/com/example/demo/Services/PostgresDbChecksHtmlService.java
src/main/java/com/example/demo/Services/AppLayoutService.java
pom.xml
```

---

## 4. Конфигурация

Файл: `src/main/resources/application.properties`

| Свойство | Пример / default | Назначение |
|----------|------------------|------------|
| `victoriametrics.base-url` | `http://localhost:8428` | Базовый URL VM (хвостовой `/` обрезается в `getBaseUrl()`) |
| `victoriametrics.time-range` | `1h` | Окно агрегации, если `from`/`to` **не** переданы |
| `victoriametrics.query-timeout-sec` | `30` | Задуман как таймаут; **сейчас не применяется к RestTemplate** |
| `victoriametrics.subquery-step` | `1m` | Шаг в подзапросах `[range:step]` (`avg_over_time` / `max_over_time`). Должен совпадать с **Min step** панели Grafana |
| `victoriametrics.cpu-rate-window` | `5m` | Окно `rate(...[X])` для CPU |
| `victoriametrics.aggregation-method` | `sum_then_percent` | Как считать % по нескольким подам одного контейнера: `sum_then_percent` или `average_per_pod` |

Класс привязки: `com.example.demo.Config.VictoriaMetricsConfig`

- Bean `victoriaMetricsRestTemplate()` — обычный `new RestTemplate()` без connect/read timeout, без interceptors.
- **Auth отсутствует** (нет Basic/Bearer/заголовков). Расчёт на доступный без авторизации VM (localhost proxy / внутренняя сеть).
- Spring relaxed binding позволяет переопределить, например, `VICTORIAMETRICS_BASE_URL` / `--victoriametrics.base-url=...`.

### Рекомендации для нового приложения

1. Реально повесить connect/read timeout на HTTP-клиент.
2. Добавить опциональный auth (Basic / Bearer) через interceptor.
3. Вынести base-url и таймауты в env для разных стендов.
4. Логировать итоговый URL + сокращённый PromQL при ошибках.

---

## 5. HTTP-клиент к VictoriaMetrics

Класс: `VictoriaMetricsClient`

VictoriaMetrics предоставляет **Prometheus-совместимый HTTP API**. В проекте используются:

| Метод Java | Внешний API | Параметры |
|------------|-------------|-----------|
| `query(String query, Long time)` | `GET {baseUrl}/api/v1/query` | `query` = PromQL; `time` = Unix **секунды** (опционально; `null` = now на стороне VM) |
| `queryRange(String query, long start, long end, long step)` | `GET {baseUrl}/api/v1/query_range` | `start`, `end`, `step` в Unix-секундах |

### Важно: рабочий путь — только instant `query`

Все доменные сервисы вызывают **только** `client.query(...)`.

Метод `queryRange` **реализован, но нигде не вызывается**. Агрегация «за интервал» делается **внутри PromQL** подзапросами:

```promql
avg_over_time(<expr>[<range>:<step>])
max_over_time(<expr>[<range>:<step>])
```

где:

- `<range>` — длительность окна (`3600s`, `1h`, …);
- `<step>` — из `victoriametrics.subquery-step` (обычно `1m`).

Так избегают огромного числа точек `query_range` и лимитов datapoints.

### Утилиты клиента

- `parseValue(List<Object> value)` — из instant-точки `[timestamp, "value"]` берёт **второй** элемент и парсит в `double` (при битых данных → `NaN`).
- `getLabel(Map, label)` — безопасное чтение лейбла из `result.metric`.

URI собирается через `UriComponentsBuilder` (корректное URL-encoding PromQL).

---

## 6. Формат ответа VM / Prometheus

DTO: `PrometheusResponse` (`@JsonIgnoreProperties(ignoreUnknown = true)`)

```text
{
  "status": "success",
  "data": {
    "resultType": "vector" | "matrix",
    "result": [
      {
        "metric": { "namespace": "...", "pod": "...", "container": "...", ... },
        "value":  [ <unix_sec>, "<number_as_string>" ],          // instant
        "values": [ [ <unix_sec>, "<number_as_string>" ], ... ]   // range (в DTO есть, в рантайме не используется)
      }
    ]
  }
}
```

Парсинг: Jackson из `spring-boot-starter-webmvc` (отдельной зависимости Prometheus/VM SDK **нет**).

Типичные ошибки при своей реализации (см. также `victoriametrics-java-app-vs-grafana.md`):

- взять timestamp вместо value;
- взять первую точку range вместо среднего по окну;
- агрегировать один series, когда в Grafana sum/avg по всем;
- сравнивать instant «сейчас» с графиком за другой диапазон.

---

## 7. Контракт времени (критично для Grafana)

### Вход в приложение (HTTP)

| Параметр | Единицы | Источник |
|----------|---------|----------|
| `from` | Unix **миллисекунды** UTC | Grafana `${__from}` |
| `to` | Unix **миллисекунды** UTC | Grafana `${__to}` |

### Внутри доменных сервисов (общий паттерн)

Если `from != null && to != null && to > from`:

```text
rangeSec            = (toMs - fromMs) / 1000
range (для PromQL)  = rangeSec + "s"          // например "3600s"
evaluationTimeSec   = toMs / 1000             // time= конца интервала
```

Иначе:

```text
range               = config.time-range       // "1h"
evaluationTimeSec   = null                   // VM оценивает «сейчас»
```

Instant-запросы всегда идут с `time = evaluationTimeSec` (если задан) — аналог оценки на правом краю окна Grafana.

### Два разных смысла времени

| Данные | Когда оцениваются |
|--------|-------------------|
| Inventory: replicas, ownership pod→workload, requests/limits, startup | **Момент `to`** (снимок) |
| Usage: avg/max CPU/MEM, throttling | **За весь интервал `[from, to]`** через subquery |

Это осознанное разделение; при рестартах подов возможна небольшая рассинхронизация (см. `docs/metrics-accuracy.md`).

---

## 8. Домен 1: Kubernetes / OpenShift (`VictoriaMetricsService`)

### Entry point

```java
List<Deployment> fetchDeployments(String namespace, Long fromMs, Long toMs)
```

### Фильтр namespace

`addNamespaceFilter(query, namespace)` вставляет `,namespace="..."` перед закрывающей `}` selector’а (с экранированием `"`). Пустой namespace → все неймспейсы.

### Шаги сбора (последовательные HTTP-запросы в VM)

1. **Replicas**
   - `kube_deployment_status_replicas_available`
   - `kube_statefulset_status_replicas_ready`
2. **Ownership**
   - Deployments: `kube_pod_owner{owner_kind="ReplicaSet"}` → `kube_replicaset_owner` (pod → RS → deployment)
   - StatefulSets: `kube_pod_owner{owner_kind="StatefulSet"}` (`owner_name` = STS)
3. **Resources (requests/limits)** на контейнер
   - `kube_pod_container_resource_requests{resource="cpu"|"memory"}`
   - `kube_pod_container_resource_limits{resource="cpu"|"memory"}`
4. **Usage за range**
   - CPU:

     ```promql
     avg_over_time(rate(container_cpu_usage_seconds_total{container!="",container!="POD"}[<cpuWindow>])[<range>:<step>])
     max_over_time(rate(container_cpu_usage_seconds_total{...}[<cpuWindow>])[<range>:<step>])
     ```

   - Memory:

     ```promql
     avg_over_time(container_memory_working_set_bytes{container!="",container!="POD"}[<range>:<step>])
     max_over_time(container_memory_working_set_bytes{...}[<range>:<step>])
     ```

5. **Throttling (CFS %)**

   ```promql
   avg_over_time(
     (
       rate(container_cpu_cfs_throttled_periods_total{container!="",container!="POD"}[<cpuWindow>])
       /
       rate(container_cpu_cfs_periods_total{...}[<cpuWindow>])
     ) * 100
     [<range>:<step>]
   )
   ```

6. **Pod startup seconds** (per pod, затем max по workload):

   ```text
   kube_pod_start_time − kube_pod_created
   ```

Контейнеры с `container=""` / `"POD"` отбрасываются (это pause/infra-контейнеры в cAdvisor).

### Агрегация в `Container` (`buildContainer`)

Ключ ряда usage: `(namespace, pod, container)`. Далее группировка в workload (Deployment / StatefulSet).

| Режим `aggregation-method` | Avg % | Max % |
|----------------------------|-------|-------|
| `sum_then_percent` (default, «как Grafana») | `sum(usage) / sum(limit)` (fallback limit→request при limit=0) | max(% по подам) |
| `average_per_pod` | среднее % по подам | max(% по подам) |

Единицы в DTO:

- CPU request/limit/use → **millicores** (`cores * 1000`)
- Memory request/limit/abs → **MB** (`bytes / 1024^2`)
- Abs use CPU max — **max по подам** (не sum)
- Throttling — max % по подам, clamp ≤ 100
- Проценты в таблице — **целые** (округление)

### Модели результата

- `Deployment`: имя, `podCount`, `startTime` (сек), `workloadType` (`Deployment` / `StatefulSet`), список `Container`
- `Container`: Rq/Lim CPU&MEM, Avg/Max % и Abs, `throttlingPercent`

### HTTP API контроллера

| Метод | Path | Параметры | Ответ |
|-------|------|-----------|-------|
| GET | `/get` | `useMetrics` (default false), `namespace`, `from`, `to` | HTML в браузере |
| GET | `/getHtml` | те же | HTML attachment |
| GET | `/getExcel` | те же | XLSX attachment |
| GET | `/getJson` | те же | `List<Deployment>` JSON |

При `useMetrics=false` данные берутся из `generateTestData()` (без обращения к VM) — удобно для UI-разработки.

Пример:

```http
GET /get?useMetrics=true&namespace=my-ns&from=1710000000000&to=1710003600000
```

Рендер: `HtmlTableService` (цвета порогов CPU/MEM/start/throttling, totals, drag-колонки, localStorage) / `ExcelTableService` (Apache POI).

---

## 9. Домен 2: Linux hosts (`NodeExporterMetricsService`)

### Entry point

```java
List<LinuxServerMetrics> fetchLinuxServerMetrics(List<String> instances, Long fromMs, Long toMs)
```

### Фильтр instance

- Параметры HTTP: `instances` и/или Grafana `var-instance` (оба списка мержатся в контроллере).
- В PromQL: `instance=~"a|b|c"` с `Pattern.quote` на каждый токен.
- Нормализация: срезаются схема `http(s)://` и порт — в данной инсталляции VM лейбл `instance` **без порта**.

### Запросы (схема range/step как выше)

Примеры:

```promql
avg_over_time((1 - avg by (instance) (rate(node_cpu_seconds_total{mode="idle",instance=~"..."}[5m]))) * 100 [<range>:<step>])
max_over_time(...тот же expr...)

avg_over_time((1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100 [<range>:<step>])
max_over_time(...тот же...)

count without (cpu, mode) (node_cpu_seconds_total{mode="idle",...})
node_memory_MemTotal_bytes{...}
node_uname_info{...}                         # лейбл release
sum by (instance) (node_tcp_connection_states{state="time_wait",...})
```

DTO `LinuxServerMetrics`: instance, cpuCores, memTotalBytes, release, tcpTimeWaitCount, cpu/mem avg&max %.

Эндпоинты: `GET /servers`, `GET /serversJson`. Рендер: `LinuxServersHtmlService`.

---

## 10. Домен 3: PostgreSQL (`PostgresDbChecksMetricsService`)

### Entry point

```java
List<PostgresQueryMetrics> fetchQueryMetrics(List<String> servers, String job, Long fromMs, Long toMs)
```

### Метрика

```promql
avg_over_time(rate(pg_stat_database_tup_returned{job=~"...",server=~"..."}[5m])[<range>:<step>])
max_over_time(rate(pg_stat_database_tup_returned{...}[5m])[<range>:<step>])
```

Особенности:

- Окно `rate` для PG **захардкожено `5m`**, не берётся из `cpu-rate-window`.
- Подпись «запроса» в строке: лейбл `query` → иначе `queryid` → иначе `datname`.
- Ключ ряда: `server + "\n" + queryLabel`.
- Фильтры: `servers` / Grafana `var-server`, опционально `job`.

DTO `PostgresQueryMetrics`: server, query, avgCount, maxCount.

Эндпоинты: `GET /dbChecks`, `GET /dbChecksJson`.  
Рендер: `PostgresDbChecksHtmlService` (порог `MAX_ALLOWED_QUERIES = 100`, green/red).

---

## 11. Зависимости (`pom.xml`)

| Артефакт | Роль относительно VM |
|----------|----------------------|
| `spring-boot-starter-webmvc` | RestTemplate, MVC, Jackson |
| `lombok` | DTO |
| `poi-ooxml` 5.2.5 | Excel-отчёты (не клиент VM) |

**Нет:** Prometheus Java client, Feign, WebClient, кэша (Caffeine), Spring Security, отдельного VM SDK.

---

## 12. Auth, кэш, ошибки (как есть)

| Тема | Факт в проекте |
|------|----------------|
| Auth | Нет |
| Кэш | Нет; каждый клик пользователя = N новых запросов в VM |
| Timeout | Свойство есть, на RestTemplate не применено |
| HTTP-ошибки VM | Нет try/catch вокруг RestTemplate → обычно 5xx наружу |
| Пустой result | Тихий skip / пустая таблица |
| NaN / Infinite | Пропускаются при заполнении карт |

Для продакшен-анализатора стоит явно разделить: транспортные ошибки vs «нет данных».

---

## 13. End-to-end data flow (шаблон для нового приложения)

### Паттерн, который стоит повторить

1. **Controller** — принять фильтры + `from`/`to` в мс (Grafana-совместимо).
2. **Domain service** — вычислить `range` и `evaluationTimeSec`.
3. **Несколько instant PromQL** — inventory (снимок) + usage (subquery за окно).
4. **Склейка по лейблам** в Map → список DTO.
5. **Renderer** — HTML / JSON / Excel из одного и того же `List<DTO>`.

### Псевдокод времени

```text
if fromMs and toMs present and toMs > fromMs:
    range = ((toMs - fromMs) / 1000) + "s"
    time  = toMs / 1000
else:
    range = defaultTimeRange   # e.g. "1h"
    time  = null               # now

result = GET {vm}/api/v1/query?query=<promql>&time=<time>
value  = Double.parse(result.data.result[i].value[1])
labels = result.data.result[i].metric
```

### Псевдокод usage-агрегации в PromQL

```text
avg_over_time( <instant_or_rate_expr> [range:step] )
max_over_time( <instant_or_rate_expr> [range:step] )
```

Не тащите весь range в память приложения, если нужна одна цифра на series — пусть считает VM.

---

## 14. Чеклист для агента: «аналогичный анализатор метрик»

### Минимальный скелет

1. Config: `base-url`, `time-range`, `subquery-step`, `rate-window`, timeouts, optional auth.
2. Client: `query` (+ опционально `query_range`), DTO ответа Prometheus, `parseValue` / `getLabel`.
3. Time helper: ms (HTTP) ↔ sec (PromQL) + построение строки `range`.
4. Domain service(s) под вашу предметную область (свои метрики и лейблы).
5. Report layer: таблицы/графики/файлы из `List<DTO>`.
6. Controllers с Grafana-параметрами `from`/`to` и фильтрами по лейблам.

### На что обратить внимание под «другой уклон» (анализатор + отчёты)

| Тема | Совет на основе этого проекта |
|------|-------------------------------|
| Совпадение с Grafana | Синхронизировать step, rate-window, момент `time`, способ агрегации по series |
| Объём данных | Prefer instant + subquery; `query_range` — только если нужны ряды для графиков |
| Единицы | Хранить канонические (cores, bytes) во внутренних DTO; форматировать в UI |
| Фильтры | Экранировать regex (`Pattern.quote`), аккуратно собирать selector |
| Inventory vs usage | Явно документировать: снимок vs окно |
| Ошибки | Отличать down VM / bad query / empty metrics |
| Производительность | Один пользовательский запрос = много PromQL; параллелить осторожно (лимиты VM) |
| Security | Не коммитить токены; auth interceptor; не светить base-url с секретами в HTML |

### Чего в этом проекте **нет**, но часто нужно анализатору

- Долговременное хранение результатов / история отчётов
- Алерты и пороги кроме простой подсветки в HTML
- AuthN/AuthZ пользователей
- Очереди / async для тяжёлых отчётов
- UI-фреймворк (Thymeleaf/React) — HTML собирается вручную
- Универсальный query builder — PromQL захардкожен под конкретные кейсы

---

## 15. Краткая шпаргалка PromQL из проекта

```promql
# K8s inventory
kube_deployment_status_replicas_available
kube_statefulset_status_replicas_ready
kube_pod_owner{owner_kind="ReplicaSet"}
kube_replicaset_owner
kube_pod_owner{owner_kind="StatefulSet"}
kube_pod_container_resource_requests{resource="cpu"}
kube_pod_container_resource_limits{resource="memory"}
kube_pod_start_time
kube_pod_created

# K8s usage / throttle
rate(container_cpu_usage_seconds_total{container!="",container!="POD"}[5m])
container_memory_working_set_bytes{container!="",container!="POD"}
rate(container_cpu_cfs_throttled_periods_total{...}[5m]) / rate(container_cpu_cfs_periods_total{...}[5m]) * 100

# Node
rate(node_cpu_seconds_total{mode="idle"}[5m])
node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes
node_uname_info
node_tcp_connection_states{state="time_wait"}

# Postgres
rate(pg_stat_database_tup_returned{...}[5m])
```

Обёртка окна:

```promql
avg_over_time(<expr>[<range>:<step>])
max_over_time(<expr>[<range>:<step>])
```

---

## 16. Итог одной фразой

Интеграция с VictoriaMetrics здесь — это **тонкий RestTemplate-клиент к `/api/v1/query`**, набор **зашитых PromQL** с агрегацией интервала через **`avg_over_time`/`max_over_time` подзапросы**, доменная склейка по лейблам в DTO и выдача **отчётных таблиц (HTML/JSON/Excel)** с контрактом времени, совместимым с Grafana (`from`/`to` в мс, оценка на `to`).
