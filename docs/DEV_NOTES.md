# NeuroLoadAnalyzer — краткие заметки

## Поток
`GET /analyze` → HTML + spinner → `GET /analyze/result` → парсинг params → плагины → VM → HTML.
`GET /analyze/json` — те же query-параметры, ответ JSON: `{ status: "<вердикт>", details: [ { name, status, children? }, … ] }`
(иерархия карточек как в HTML; у листьев только name+status).

## Вход
- Обязательны: `from`, `to` (мс, Grafana).
- Остальное — произвольные параметры; одинаковые имена допускаются (multi-value).
- Имена `Тип_Софт_Назначение` (пример `VM_Kafka_GW`). Префикс Grafana `var-` снимается.
- Опциональные цели: суффикс `_opt` / `_OPT` (пример `VM_Kafka_GW_opt=host`). Те же VM-плагины; карточка видна (HTML: ` (opt)`, JSON: `"optional": true`), но статус **не** поднимается к purpose/software/type и **не** влияет на вердикт.
- K8S: `k8s_namespace=<имя>` (можно несколько; обрабатываются по очереди). Группы VM/K8S в отчёте — только если есть соответствующие результаты.

## Плагин
`name` + `targetTypePrefix` + `QueryMode` (INSTANT|RANGE) + `promQlTemplate` (`$VM`, `$range`, `$step`)
+ `ThresholdCondition` / `BandedThresholdCondition` (instant) или series-условие (range).
K8S: `k8sThreshold` / `k8sSeries` + `WorkloadMetric`.

### Статусы и aggregate
Цепочка (худший → лучший): **`FAIL > WARN > NO_DATA > OK > SKIP > INFO`**.
- INFO поднимает родителя **только** если все потомки INFO.
- Summary: `Fail | Warn | No Data | OK | Skip | Info` (синий Info).

### Вердикт отчёта
Считается по статусам **top-блоков** (`TypeReportGroup`: VM и/или K8S) через `StatusAggregator`, затем map:
`FAIL→Неуспешно`, `WARN|INFO→С замечаниями`, `NO_DATA|SKIP→Недостаточно данных`, `OK→Успешно`, пусто→Недостаточно данных.
Классы: `AnalysisVerdict`, `VerdictMapper`. Баннер над summary в HTML.

### Полосы (`BandedThresholdCondition`)
- CPU/RAM: <78 OK · [78; 80) WARN · ≥80 FAIL.
- Throttle %: ≤1 OK · (1; 3] INFO · (3; 7] WARN · >7 FAIL.
- TCP: ≤12k OK · (12k; 16k) INFO · ≥16k FAIL.
  PromQL: `sum(...state...) or vector(0)` — отсутствие серии = 0 → OK (не No Data).
- Restarts: single `> 0` → FAIL.
- Throttling trend: OK / FAIL(рост) / INFO(мало данных).

### VM / K8S CPU max
Две проверки с одинаковыми bands 78/80:
- **[5m]** — устойчивый max (`rate[5m]` → `max_over_time`); короткие минутные пики сглаживаются.
- **[1m]** — точнее для пиков ~1м (`rate[1m]` → `max_over_time`); всё ещё среднее за минуту, не irate по scrape.
Имена: VM `CPU max [5m]` / `CPU max [1m]`; K8S `CPU usage [5m]` / `CPU usage [1m]`.

### VM CPU spike + reboot (деструктивные кейсы)
- **CPU spike [irate]** — `irate(...idle...[2m])` → `max_over_time([$range:15s])`, bands 78/80.
  Ловит краткие пики, попавшие в scrape (то, что видно на коротком графике и «съедается» при зуме).
  Не восстанавливает события короче интервала scrape.
- **Unexpected reboot** — `max(changes(node_boot_time_seconds[$range])) > 0` → FAIL.
  Ловит ребут в окне теста (в т.ч. после атаки, когда CPU-пик мог не сохраниться).

### VM RAM usage
Peak % за from–to (gauge, без `rate`): `max_over_time((100*(1-MemAvailable/MemTotal))[$range:$step])`, bands 78/80.
Не снимок на `to` — ловит пик за весь интервал.

### Follow-up (не реализовано)
**CPU time above 80%** — доля времени CPU > 80%: ≤1% OK · (1%; 5%] INFO · >5% FAIL.

### RAM growth / leak
- PromQL: used bytes (`MemTotal - MemAvailable`), часто с `avg_over_time(...[5m:1m])`.
- `query_range` + Sen’s slope / Mann–Kendall, warmup 1ч, min window 4ч.
- Baseline = медиана первых точек (не первая — меньше ложных WARN от провалов).
- Пороги (калибровка под 12ч; шум ±0.5 п.п. утилизации хоста → OK):
  - warn ≥ **0.25**/ч, fail ≥ **0.75**/ч (% от baseline used);
  - min Δ ≥ **256 MiB** и ≥ **1%** baseline used.

## Каталог правил
- Интерфейс `AnalysisPluginCatalog`.
- `ExamplePluginCatalog` — в git (демо/CI).
- `LocalPluginCatalog` — локальные правила, **в .gitignore** (не пушить).
- Шаблон: `docs/examples/LocalPluginCatalog.example.java` → копировать в `plugin/catalog/LocalPluginCatalog.java`.

## Todo.md (ориентиры метрик)
VM: CPU/RAM/Disk?/TCP; Kafka: lag; PG: connects?; DA: CPU/RAM/throttling/resources; transactions: load/errors/latency.
