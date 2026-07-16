# NeuroLoadAnalyzer — краткие заметки

## Поток
`GET /analyze` → HTML + spinner → `GET /analyze/result` → парсинг params → плагины → VM → HTML.

## Вход
- Обязательны: `from`, `to` (мс, Grafana).
- Остальное — произвольные параметры; одинаковые имена допускаются (multi-value).
- Имена `Тип_Софт_Назначение` (пример `VM_Kafka_GW`). Префикс Grafana `var-` снимается.

## Плагин
`name` + `targetTypePrefix` (напр. `VM`) + `promQlTemplate` (`$VM`) + `ThresholdCondition`.
Для каждого параметра с типом `VM` × каждый VM-плагин → отдельный запрос к VM.
Статусы: OK / Fail / No Data / Skip. Отчёт группируется по `software` (Kafka, Postgre, …).

## Каталог правил
- Интерфейс `AnalysisPluginCatalog`.
- `ExamplePluginCatalog` — в git (демо/CI).
- `LocalPluginCatalog` — локальные правила, **в .gitignore** (не пушить).
- Шаблон: `docs/examples/LocalPluginCatalog.example.java` → копировать в `plugin/catalog/LocalPluginCatalog.java`.

## Todo.md (ориентиры метрик)
VM: CPU/RAM/Disk?/TCP; Kafka: lag; PG: connects?; DA: CPU/RAM/throttling/resources; transactions: load/errors/latency.
