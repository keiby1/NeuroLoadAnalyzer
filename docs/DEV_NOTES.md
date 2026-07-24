# NeuroLoadAnalyzer — краткие заметки

## Поток
`GET /analyze` → HTML + spinner → `GET /analyze/result` → парсинг params → плагины → VM → HTML.

## Вход
- Обязательны: `from`, `to` (мс, Grafana).
- Остальное — произвольные параметры; одинаковые имена допускаются (multi-value).
- Имена `Тип_Софт_Назначение` (пример `VM_Kafka_GW`). Префикс Grafana `var-` снимается.
- K8S: `k8s_namespace=<имя>` (можно несколько; обрабатываются по очереди). Группы VM/K8S в отчёте — только если есть соответствующие результаты.

## Плагин
`name` + `targetTypePrefix` + `QueryMode` (INSTANT|RANGE) + `promQlTemplate` (`$VM`, `$range`, `$step`)
+ `ThresholdCondition` / `BandedThresholdCondition` (instant) или series-условие (range).
K8S: `k8sThreshold` / `k8sSeries` + `WorkloadMetric`.

### Статусы и aggregate
Цепочка (худший → лучший): **`FAIL > WARN > NO_DATA > OK > SKIP > INFO`**.
- INFO поднимает родителя **только** если все потомки INFO.
- Summary: `Fail | Warn | No Data | OK | Skip | Info` (синий Info).

### Полосы (`BandedThresholdCondition`)
- CPU/RAM: <78 OK · [78; 80) WARN · ≥80 FAIL.
- Throttle %: ≤1 OK · (1; 3] INFO · (3; 7] WARN · >7 FAIL.
- TCP: ≤12k OK · (12k; 16k) INFO · ≥16k FAIL.
  PromQL: `sum(...state...) or vector(0)` — отсутствие серии = 0 → OK (не No Data).
- Restarts: single `> 0` → FAIL.
- Throttling trend: OK / FAIL(рост) / INFO(мало данных).

### VM CPU max
Сглаженный max за from–to: `max_over_time((100 - avg(rate(...idle...[5m]))*100)[$range:$step])`, bands 80/90.
Имя в отчёте: **CPU max**.

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
