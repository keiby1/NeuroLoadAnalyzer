# NeuroLoadAnalyzer — архитектура и план

Документ фиксирует целевое поведение приложения на основе первичного ТЗ.
Детали плагинов анализа и HTML-отчёта будут уточняться отдельно.

---

## 1. Назначение

**NeuroLoadAnalyzer** — Spring Boot приложение, которое:

1. Принимает HTTP-запрос (как правило из **Grafana**: iframe / link) с временным интервалом и набором переменных.
2. Выполняет Prometheus-compatible запросы в **VictoriaMetrics**.
3. Прогоняет полученные ряды через набор **плагинов анализа** (правила).
4. Отдаёт **HTML-отчёт** с индикаторами («светофоры»).

Пока реализован каркас: HTML-вход с индикатором загрузки + транспортный клиент VM.
Доменные PromQL, плагины и полноценный отчёт — следующие шаги.

---

## 2. Зафиксированные решения

| Тема | Решение |
|------|---------|
| Эндпоинт | `GET /analyze` |
| UI ответа | HTML: сначала круговая анимация загрузки, затем фрагмент результата |
| Заглушка результата | `<p>Тут будет результат</p>` |
| `query` vs `query_range` | отложено до реализации плагинов и конкретных запросов |
| Auth к VictoriaMetrics | **без auth** (внутренняя сеть) |
| Копирование ZK_tablecreater | по `VICTORIAMETRICS.md`, без обязательного 1-в-1 копирования исходников |

---

## 3. Поток данных (текущий + целевой)

```
Grafana / клиент
        │  GET /analyze?from=...&to=...&var-...=...
        ▼
AnalysisController
        │  сразу отдаёт HTML-оболочку со spinner
        │
        │  (браузер) fetch GET /analyze/result?...  ← те же query-параметры
        ▼
AnalysisService             ← оркестрация (пока stub)
        │
        ├─ MetricQueryService / доменные сервисы   ← PromQL (позже)
        │         │
        │         ▼
        │  VictoriaMetricsClient                  ← RestTemplate → VM /api/v1/query
        │
        ├─ AnalysisPlugin[]                       ← правила (позже)
        │
        └─ AnalysisPageService                    ← HTML загрузки / результат
                │
                ▼
            HTML-фрагмент отчёта
```

**Принцип (как в ZK_tablecreater):** тонкий transport-клиент к VM + толстые доменные сервисы с PromQL и бизнес-логикой.

---

## 4. Контракт входящего REST API

### Эндпоинты

| Метод | Path | Ответ |
|-------|------|-------|
| GET | `/analyze` | HTML-оболочка со spinner; JS запрашивает результат |
| GET | `/analyze/result` | HTML-фрагмент результата анализа |

```http
GET /analyze?from={ms}&to={ms}&...произвольные параметры...
GET /analyze/result?from={ms}&to={ms}&...те же параметры...
```

| Параметр | Обязательность | Единицы / смысл |
|----------|----------------|-----------------|
| `from` | да | Unix **миллисекунды** UTC (Grafana `${__from}`) |
| `to` | да | Unix **миллисекунды** UTC (Grafana `${__to}`) |
| остальные | нет | заранее неизвестны; типично Grafana `var-*` |

Все query-параметры, кроме `from`/`to`, сохраняются как **карта фильтров/переменных** и позже используются для параметризации PromQL.

Пример:

```http
GET /analyze?from=1710000000000&to=1710003600000&var-type=type1&var-server=host-a
```

### Ответ (текущий каркас)

1. `/analyze` — страница с круговой анимацией «Выполняется анализ…».
2. `/analyze/result` — пока заглушка `<p>Тут будет результат</p>`; позже полноценный HTML-отчёт со светофорами.
---

## 5. Работа с VictoriaMetrics

Ориентир: `VICTORIAMETRICS.md` (реализация в ZK_tablecreater).

### Транспорт

| Метод | API | Назначение |
|-------|-----|------------|
| `query(promql, timeSec)` | `GET /api/v1/query` | **Основной** путь (instant) |
| `queryRange(...)` | `GET /api/v1/query_range` | Зарезервирован (ряды для графиков) |

Агрегация за интервал — через PromQL subquery, а не через выгрузку всего `query_range`:

```promql
avg_over_time(<expr>[<range>:<step>])
max_over_time(<expr>[<range>:<step>])
```

### Время

```text
если from и to заданы и to > from:
    rangeSec           = (toMs - fromMs) / 1000
    rangeForPromQL     = rangeSec + "s"
    evaluationTimeSec  = toMs / 1000          # оценка на правом краю окна Grafana
иначе:
    rangeForPromQL     = config.time-range    # например "1h"
    evaluationTimeSec  = null                 # «сейчас» на стороне VM
```

### Параметризация запросов (план)

Запрос к VM состоит из двух частей:

1. **Хардкод** — шаблон PromQL / имя метрики, например `someMetrics{type="${type}"}`.
2. **Динамика** — подстановка значений из входящих параметров (`type1` и т.д.).

Конкретный каталог шаблонов и маппинг `var-*` → плейсхолдеры — на следующих итерациях.

### Конфигурация (`application.properties`)

| Свойство | Default | Назначение |
|----------|---------|------------|
| `victoriametrics.base-url` | `http://localhost:8428` | Базовый URL VM |
| `victoriametrics.time-range` | `1h` | Окно, если нет from/to |
| `victoriametrics.subquery-step` | `1m` | Шаг `[range:step]` |
| `victoriametrics.connect-timeout-ms` | `5000` | Connect timeout HTTP |
| `victoriametrics.read-timeout-ms` | `30000` | Read timeout HTTP |

Auth **не используется** (доступ во внутренней сети).

Выбор между instant `query` и `query_range` будет сделан при реализации конкретных плагинов.

---

## 6. Плагины анализа

Плагин (`AnalysisPlugin`) содержит:

1. **название правила** (например `CPU > 80%`, `RAM growth / leak`);
2. **targetTypePrefix** — тип целей, напр. `VM` → все параметры `VM_*`;
3. **QueryMode** — `INSTANT` или `RANGE`;
4. **PromQL-шаблон** с плейсхолдером `$VM`;
5. **условие** — `ThresholdCondition` (instant) или `TrendLeakCondition` (range).

### Матчинг и выполнение запросов

Пример: 3 параметра `VM_*` + 3 VM-плагина (CPU, RAM ceiling, RAM leak) → **9** запросов к VictoriaMetrics.

Статусы результата:

| Статус | Смысл |
|--------|--------|
| OK | превышения/утечки нет |
| Warn | подозрение / мало данных / late-onset / step-change |
| Fail | превышение или подтверждённая утечка |
| No Data | запрос успешен, данных нет |
| Skip | ошибка, данные получить не удалось |

Агрегация: **Fail > Warn > NoData > OK**, Skip non-blocking. Leak в UI: slope / Δ / reason.

Отчёт группируется по **типу ПО** (`software` из `Тип_Софт_Назначение`): внутри — параметр, значение, правило, статус.

### Каталог (важно для git)

| Класс | В git? | Назначение |
|-------|--------|------------|
| `AnalysisPluginCatalog` | да | интерфейс |
| `ExamplePluginCatalog` | да | демо/CI |
| `LocalPluginCatalog` | **нет** (gitignore) | локальные/секретные правила |
| `docs/examples/LocalPluginCatalog.example.java` | да | шаблон для копирования |

`PluginCatalogConfiguration` загружает `LocalPluginCatalog` через reflection; если класса нет — `ExamplePluginCatalog`.

---

## 7. HTML-отчёт

Стили карточек — как в `ExampleReport.html` (green/red/yellow/gray, expand/collapse).

Иерархия:
`Тип` (VM → «Виртуальные сервера», маппинг `TargetTypeLabels`)
→ `Софт` → `Назначение` → `значение параметра` → проверки (правила).

Агрегация статусов вверх (`StatusAggregator`, политика non-blocking Skip):
`Fail > Warn > NoData > OK`; `Skip` учитывается только если нет решающих статусов.
Цвета: OK=green, Warn=orange, Fail=red, NoData=yellow, Skip=gray.

---

## 8. Структура пакетов (текущий каркас)

```
com.nla.NeuroLoadAnalyzer
├── NeuroLoadAnalyzerApplication
├── config
│   ├── VictoriaMetricsConfig
│   └── VictoriaMetricsProperties
├── client
│   └── VictoriaMetricsClient
├── controller
│   └── AnalysisController
├── dto
│   ├── AnalysisRequest
│   ├── AnalysisReport
│   ├── TypedTarget
│   └── PrometheusResponse
├── plugin
│   ├── AnalysisPlugin / AnalysisCondition / ThresholdCondition
│   ├── AnalysisPluginCatalog / PluginCatalogConfiguration
│   ├── PromQlBinder / PluginAnalysisService / PluginResult
│   └── catalog/ExamplePluginCatalog (+ LocalPluginCatalog локально)
├── service
│   ├── AnalysisService
│   ├── AnalysisPageService
│   └── RequestVariableParser
└── util
    └── TimeRange
```

---

## 9. Этапы реализации

| # | Этап | Статус |
|---|------|--------|
| 1 | Документация + HTML `/analyze` (loader) + клиент VM | сделано |
| 2 | Парсинг params + каркас плагинов + каталог (local gitignored) | **сейчас** |
| 3 | Расширение условий / агрегация multi-series | позже |
| 4 | HTML-отчёт со светофорами | позже |

---

## 10. Открытые вопросы (оставшиеся)

- окончательный вид Grafana-параметров (`var-*` и др.);
- выбор `query` / `query_range` — при реализации плагинов;
- макет HTML-отчёта со светофорами.
