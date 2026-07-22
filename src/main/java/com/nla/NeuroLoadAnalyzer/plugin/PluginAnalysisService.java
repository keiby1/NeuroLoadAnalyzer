package com.nla.NeuroLoadAnalyzer.plugin;

import com.nla.NeuroLoadAnalyzer.client.VictoriaMetricsClient;
import com.nla.NeuroLoadAnalyzer.dto.AnalysisRequest;
import com.nla.NeuroLoadAnalyzer.dto.K8sNamespaceTarget;
import com.nla.NeuroLoadAnalyzer.dto.PrometheusResponse;
import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import com.nla.NeuroLoadAnalyzer.dto.k8s.K8sWorkload;
import com.nla.NeuroLoadAnalyzer.service.RequestVariableParser;
import com.nla.NeuroLoadAnalyzer.service.k8s.K8sWorkloadService;
import com.nla.NeuroLoadAnalyzer.util.TimeRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runs VM plugins (PromQL) and K8S plugins (inventory + threshold on workload metrics).
 */
@Service
public class PluginAnalysisService {

	private static final Logger log = LoggerFactory.getLogger(PluginAnalysisService.class);

	private final AnalysisPluginCatalog pluginCatalog;
	private final RequestVariableParser variableParser;
	private final VictoriaMetricsClient victoriaMetricsClient;
	private final K8sWorkloadService k8sWorkloadService;

	public PluginAnalysisService(
			AnalysisPluginCatalog pluginCatalog,
			RequestVariableParser variableParser,
			VictoriaMetricsClient victoriaMetricsClient,
			K8sWorkloadService k8sWorkloadService) {
		this.pluginCatalog = pluginCatalog;
		this.variableParser = variableParser;
		this.victoriaMetricsClient = victoriaMetricsClient;
		this.k8sWorkloadService = k8sWorkloadService;
	}

	public List<PluginResult> runAll(AnalysisRequest request, TimeRange timeRange) {
		List<AnalysisPlugin> plugins = pluginCatalog.getPlugins();
		List<PluginResult> results = new ArrayList<>();

		results.addAll(runVmPlugins(request, timeRange, plugins));
		results.addAll(runK8sPlugins(request, timeRange, plugins));

		log.info("Plugin runs finished: executed={}", results.size());
		return List.copyOf(results);
	}

	private List<PluginResult> runVmPlugins(
			AnalysisRequest request, TimeRange timeRange, List<AnalysisPlugin> plugins) {
		List<TypedTarget> targets = variableParser.extractTypedTargets(request.getParameters()).stream()
				.filter(t -> !"K8S".equalsIgnoreCase(t.type()))
				.toList();
		List<AnalysisPlugin> vmPlugins = plugins.stream()
				.filter(p -> !p.isK8sWorkloadCheck())
				.toList();

		List<PlannedRun> planned = new ArrayList<>();
		for (TypedTarget target : targets) {
			for (AnalysisPlugin plugin : vmPlugins) {
				if (plugin.appliesTo(target.type())) {
					planned.add(new PlannedRun(plugin, target));
				}
			}
		}
		logPlannedRules(planned);

		List<PluginResult> results = new ArrayList<>(planned.size());
		for (PlannedRun run : planned) {
			results.add(runOne(run.plugin(), run.target(), timeRange));
		}
		return results;
	}

	private List<PluginResult> runK8sPlugins(
			AnalysisRequest request, TimeRange timeRange, List<AnalysisPlugin> plugins) {
		List<K8sNamespaceTarget> namespaces = variableParser.extractK8sNamespaces(request.getParameters());
		List<AnalysisPlugin> k8sPlugins = plugins.stream()
				.filter(AnalysisPlugin::isK8sWorkloadCheck)
				.toList();
		if (namespaces.isEmpty() || k8sPlugins.isEmpty()) {
			if (!namespaces.isEmpty()) {
				log.info("K8S namespaces present but no K8S plugins registered: {}", namespaces.size());
			}
			return List.of();
		}

		log.info("K8S namespaces: count={}, [{}]",
				namespaces.size(),
				namespaces.stream().map(K8sNamespaceTarget::namespace).collect(Collectors.joining(", ")));
		log.info("K8S plugins to run: {}",
				k8sPlugins.stream().map(AnalysisPlugin::name).collect(Collectors.joining(", ")));

		List<PluginResult> results = new ArrayList<>();
		boolean needThrottlingTrend = k8sPlugins.stream()
				.anyMatch(p -> p.workloadMetric() == WorkloadMetric.K8S_THROTTLING_TREND);
		int trendStepMinutes = k8sPlugins.stream()
				.filter(p -> p.workloadMetric() == WorkloadMetric.K8S_THROTTLING_TREND)
				.mapToInt(AnalysisPlugin::stepMinutes)
				.min()
				.orElse(5);

		for (K8sNamespaceTarget nsTarget : namespaces) {
			try {
				List<K8sWorkload> workloads = k8sWorkloadService.fetchWorkloads(nsTarget.namespace(), timeRange);
				Map<String, List<MetricPoint>> throttlingTrends = needThrottlingTrend
						? k8sWorkloadService.fetchThrottlingTrendSeries(
						nsTarget.namespace(), timeRange, trendStepMinutes)
						: Map.of();
				if (workloads.isEmpty()) {
					for (AnalysisPlugin plugin : k8sPlugins) {
						results.add(PluginResult.noDataK8s(
								plugin, nsTarget.namespace(), "(no workloads)",
								"namespace=" + nsTarget.namespace()));
					}
					continue;
				}
				for (K8sWorkload workload : workloads) {
					for (AnalysisPlugin plugin : k8sPlugins) {
						if (plugin.workloadMetric() == WorkloadMetric.K8S_THROTTLING_TREND) {
							results.add(evaluateK8sThrottlingTrend(
									plugin, workload, timeRange, throttlingTrends));
						} else {
							results.add(evaluateK8sPlugin(plugin, workload, timeRange));
						}
					}
				}
			} catch (RestClientException e) {
				log.warn("K8S fetch failed for namespace={}: {}", nsTarget.namespace(), e.getMessage());
				for (AnalysisPlugin plugin : k8sPlugins) {
					results.add(PluginResult.skipK8s(
							plugin, nsTarget.namespace(), "*",
							"Ошибка запроса к VictoriaMetrics: " + e.getMessage()));
				}
			} catch (RuntimeException e) {
				log.warn("K8S analysis failed for namespace={}: {}", nsTarget.namespace(), e.getMessage());
				for (AnalysisPlugin plugin : k8sPlugins) {
					results.add(PluginResult.skipK8s(
							plugin, nsTarget.namespace(), "*",
							"Ошибка выполнения: " + e.getMessage()));
				}
			}
		}
		return results;
	}

	private PluginResult evaluateK8sThrottlingTrend(
			AnalysisPlugin plugin,
			K8sWorkload workload,
			TimeRange timeRange,
			Map<String, List<MetricPoint>> throttlingTrends) {
		String queryDoc = plugin.promQlTemplate()
				.replace("$namespace", workload.namespace())
				.replace("$deployment", workload.name())
				.replace("$range", timeRange.rangeForPromQl());
		if (timeRange.fromMs() == null || timeRange.toMs() == null || !timeRange.hasExplicitWindow()) {
			return PluginResult.skipK8s(plugin, workload.namespace(), workload.name(),
					"Для анализа тренда троттлинга нужны from/to во входном запросе");
		}
		String key = K8sWorkloadService.workloadSeriesKey(workload.workloadType(), workload.name());
		List<MetricPoint> series = throttlingTrends.getOrDefault(key, List.of());
		if (series.isEmpty()) {
			log.info("K8S throttling trend: no series for ns={}, workload={}",
					workload.namespace(), workload.name());
			return PluginResult.noDataK8s(plugin, workload.namespace(), workload.name(), queryDoc);
		}
		SeriesVerdict verdict = plugin.seriesCondition().evaluate(series);
		log.info("K8S check: rule='{}', ns={}, workload={} ({}), status={}, reason={}",
				plugin.name(), workload.namespace(), workload.name(), workload.workloadType(),
				verdict.status(), verdict.reason());
		return PluginResult.fromSeriesK8s(
				plugin,
				workload.namespace(),
				workload.name(),
				workload.workloadType(),
				queryDoc,
				verdict);
	}

	private PluginResult evaluateK8sPlugin(AnalysisPlugin plugin, K8sWorkload workload, TimeRange timeRange) {
		double value;
		String queryDoc = plugin.promQlTemplate()
				.replace("$namespace", workload.namespace())
				.replace("$deployment", workload.name())
				.replace("$range", timeRange.rangeForPromQl());
		switch (plugin.workloadMetric()) {
			case K8S_CPU_MAX_PERCENT -> value = workload.maxCpuPercent();
			case K8S_MEM_MAX_PERCENT -> value = workload.maxMemPercent();
			case K8S_RESTART_INCREASE -> value = workload.totalRestartIncrease();
			case K8S_THROTTLING_MAX_PERCENT -> value = workload.maxThrottlingPercent();
			default -> {
				return PluginResult.skipK8s(plugin, workload.namespace(), workload.name(),
						"Неизвестный WorkloadMetric: " + plugin.workloadMetric());
			}
		}
		boolean fail = plugin.thresholdCondition().isViolation(value);
		log.info("K8S check: rule='{}', ns={}, workload={} ({}), value={}, fail={}",
				plugin.name(), workload.namespace(), workload.name(), workload.workloadType(), value, fail);
		return PluginResult.evaluatedK8s(
				plugin,
				workload.namespace(),
				workload.name(),
				workload.workloadType(),
				queryDoc,
				value,
				fail);
	}

	private void logPlannedRules(List<PlannedRun> planned) {
		if (planned.isEmpty()) {
			log.info("Matched VM rules to execute: count=0");
			return;
		}
		String formatted = planned.stream()
				.map(run -> run.plugin().name()
						+ " [" + run.plugin().targetTypePrefix() + "/" + run.plugin().queryMode() + "]"
						+ " <- " + run.target().canonicalName() + "=" + run.target().value())
				.collect(Collectors.joining("; "));
		log.info("Matched VM rules to execute: count={}, [{}]", planned.size(), formatted);
	}

	private PluginResult runOne(AnalysisPlugin plugin, TypedTarget target, TimeRange timeRange) {
		PromQlBinder.BindResult bind = PromQlBinder.bind(
				plugin.promQlTemplate(),
				name -> resolvePlaceholder(name, plugin, target));

		if (!bind.complete()) {
			log.warn("VM query skipped (incomplete bind): rule='{}', param={}={}, missing={}",
					plugin.name(), target.canonicalName(), target.value(), bind.missingPlaceholders());
			return PluginResult.skip(plugin, target,
					"Не хватает плейсхолдеров: " + String.join(", ", bind.missingPlaceholders()));
		}

		String query = bind.boundQuery();
		log.info("VM parameterized query: rule='{}', mode={}, param={}={}, query={}",
				plugin.name(), plugin.queryMode(), target.canonicalName(), target.value(), query);

		try {
			if (plugin.queryMode() == QueryMode.RANGE) {
				return runRange(plugin, target, timeRange, query);
			}
			return runInstant(plugin, target, timeRange, query);
		} catch (RestClientException e) {
			log.info("VM request status: FAILED (rule='{}', param={}, reason={})",
					plugin.name(), target.canonicalName(), e.getMessage());
			return PluginResult.skip(plugin, target, "Ошибка запроса к VictoriaMetrics: " + e.getMessage());
		} catch (RuntimeException e) {
			log.info("VM request status: FAILED (rule='{}', param={}, reason={})",
					plugin.name(), target.canonicalName(), e.getMessage());
			return PluginResult.skip(plugin, target, "Ошибка выполнения: " + e.getMessage());
		}
	}

	private PluginResult runInstant(
			AnalysisPlugin plugin, TypedTarget target, TimeRange timeRange, String query) {
		PrometheusResponse response = victoriaMetricsClient.query(query, timeRange.evaluationTimeSec());
		if (!isSuccess(response)) {
			String err = response == null ? "пустой ответ"
					: (response.getError() != null ? response.getError() : response.getStatus());
			log.info("VM request status: FAILED (rule='{}', param={})", plugin.name(), target.canonicalName());
			return PluginResult.skip(plugin, target, "VictoriaMetrics вернула ошибку: " + err);
		}

		List<Double> values = collectFiniteInstantValues(response);
		if (values.isEmpty()) {
			log.info("VM request status: SUCCESS (rule='{}', param={}, datapoints=0)",
					plugin.name(), target.canonicalName());
			return PluginResult.noData(plugin, target, query);
		}

		double metricValue = values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
		boolean fail = values.stream().anyMatch(plugin.thresholdCondition()::isViolation);
		log.info("VM request status: SUCCESS (rule='{}', param={}, datapoints={}, value={}, analysisFail={})",
				plugin.name(), target.canonicalName(), values.size(), metricValue, fail);
		return PluginResult.evaluated(plugin, target, query, metricValue, fail);
	}

	private PluginResult runRange(
			AnalysisPlugin plugin, TypedTarget target, TimeRange timeRange, String query) {
		if (timeRange.fromMs() == null || timeRange.toMs() == null || !timeRange.hasExplicitWindow()) {
			return PluginResult.skip(plugin, target,
					"Для RANGE-анализа нужны from/to во входном запросе");
		}

		long startSec = timeRange.fromMs() / 1000L;
		long endSec = timeRange.toMs() / 1000L;
		long stepSec = Math.max(60L, plugin.stepMinutes() * 60L);

		PrometheusResponse response = victoriaMetricsClient.queryRange(query, startSec, endSec, stepSec);
		if (!isSuccess(response)) {
			String err = response == null ? "пустой ответ"
					: (response.getError() != null ? response.getError() : response.getStatus());
			log.info("VM request status: FAILED (rule='{}', param={})", plugin.name(), target.canonicalName());
			return PluginResult.skip(plugin, target, "VictoriaMetrics вернула ошибку: " + err);
		}

		List<MetricPoint> series = collectRangeSeries(response);
		if (series.isEmpty()) {
			log.info("VM request status: SUCCESS (rule='{}', param={}, datapoints=0)",
					plugin.name(), target.canonicalName());
			return PluginResult.noData(plugin, target, query);
		}

		SeriesVerdict verdict = plugin.seriesCondition().evaluate(series);
		log.info("VM request status: SUCCESS (rule='{}', param={}, datapoints={}, verdict={}, reason={})",
				plugin.name(), target.canonicalName(), series.size(), verdict.status(), verdict.reason());
		return PluginResult.fromSeries(plugin, target, query, verdict);
	}

	private static Optional<String> resolvePlaceholder(
			String placeholderName,
			AnalysisPlugin plugin,
			TypedTarget target) {
		if (placeholderName == null) {
			return Optional.empty();
		}
		if (placeholderName.equalsIgnoreCase(plugin.targetTypePrefix())
				|| placeholderName.equalsIgnoreCase(target.type())) {
			return Optional.ofNullable(target.value());
		}
		return Optional.empty();
	}

	private static boolean isSuccess(PrometheusResponse response) {
		return response != null && "success".equalsIgnoreCase(response.getStatus());
	}

	private static List<Double> collectFiniteInstantValues(PrometheusResponse response) {
		if (response.getData() == null || response.getData().getResult() == null) {
			return List.of();
		}
		List<Double> values = new ArrayList<>();
		for (PrometheusResponse.Result result : response.getData().getResult()) {
			double value = VictoriaMetricsClient.parseValue(result.getValue());
			if (Double.isFinite(value)) {
				values.add(value);
			}
		}
		return values;
	}

	static List<MetricPoint> collectRangeSeries(PrometheusResponse response) {
		if (response.getData() == null || response.getData().getResult() == null) {
			return List.of();
		}
		java.util.Map<Long, Double> byTs = new java.util.TreeMap<>();
		for (PrometheusResponse.Result result : response.getData().getResult()) {
			List<List<Object>> values = result.getValues();
			if (values == null) {
				continue;
			}
			for (List<Object> pair : values) {
				if (pair == null || pair.size() < 2 || pair.get(0) == null || pair.get(1) == null) {
					continue;
				}
				long ts;
				try {
					ts = (long) Double.parseDouble(pair.get(0).toString());
				} catch (NumberFormatException e) {
					continue;
				}
				double value;
				try {
					value = Double.parseDouble(pair.get(1).toString());
				} catch (NumberFormatException e) {
					continue;
				}
				if (!Double.isFinite(value)) {
					continue;
				}
				byTs.merge(ts, value, Math::max);
			}
		}
		return byTs.entrySet().stream()
				.map(e -> new MetricPoint(e.getKey(), e.getValue()))
				.sorted(Comparator.comparingLong(MetricPoint::timestampSec))
				.toList();
	}

	private record PlannedRun(AnalysisPlugin plugin, TypedTarget target) {
	}
}
