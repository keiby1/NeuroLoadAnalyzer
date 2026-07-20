# NeuroLoadAnalyzer — краткие заметки

## Поток
`GET /analyze` → HTML + spinner → `GET /analyze/result` → парсинг params → плагины → VM → HTML.

## Вход
- Обязательны: `from`, `to` (мс, Grafana).
- Остальное — произвольные параметры; одинаковые имена допускаются (multi-value).
- Имена `Тип_Софт_Назначение` (пример `VM_Kafka_GW`). Префикс Grafana `var-` снимается.

## Плагин
`name` + `targetTypePrefix` + `QueryMode` (INSTANT|RANGE) + `promQlTemplate` (`$VM`)
+ `ThresholdCondition` (instant) или `TrendLeakCondition` (range).
Статусы: OK / Warn / Fail / No Data / Skip.
Агрегация: `Fail > Warn > NoData > OK`, Skip non-blocking.

### RAM growth / leak
- PromQL: used bytes (`MemTotal - MemAvailable`), часто с `avg_over_time(...[5m:1m])`.
- `query_range` + Sen’s slope / Mann–Kendall, warmup 1ч, min window 4ч.
- Пороги (калибровать на 12ч прогонах): warn ≥0.05%/ч, fail ≥0.20%/ч, min Δ ≈75 МиБ.

## Каталог правил
- Интерфейс `AnalysisPluginCatalog`.
- `ExamplePluginCatalog` — в git (демо/CI).
- `LocalPluginCatalog` — локальные правила, **в .gitignore** (не пушить).
- Шаблон: `docs/examples/LocalPluginCatalog.example.java` → копировать в `plugin/catalog/LocalPluginCatalog.java`.

## Todo.md (ориентиры метрик)
VM: CPU/RAM/Disk?/TCP; Kafka: lag; PG: connects?; DA: CPU/RAM/throttling/resources; transactions: load/errors/latency.
