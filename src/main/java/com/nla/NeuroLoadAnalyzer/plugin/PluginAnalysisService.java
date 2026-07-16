package com.nla.NeuroLoadAnalyzer.plugin;

import com.nla.NeuroLoadAnalyzer.client.VictoriaMetricsClient;
import com.nla.NeuroLoadAnalyzer.dto.AnalysisRequest;
import com.nla.NeuroLoadAnalyzer.dto.PrometheusResponse;
import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import com.nla.NeuroLoadAnalyzer.service.RequestVariableParser;
import com.nla.NeuroLoadAnalyzer.util.TimeRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runs each applicable plugin against every matching typed target (e.g. all {@code VM_*} params).
 */
@Service
public class PluginAnalysisService {

	private static final Logger log = LoggerFactory.getLogger(PluginAnalysisService.class);

	private final AnalysisPluginCatalog pluginCatalog;
	private final RequestVariableParser variableParser;
	private final VictoriaMetricsClient victoriaMetricsClient;

	public PluginAnalysisService(
			AnalysisPluginCatalog pluginCatalog,
			RequestVariableParser variableParser,
			VictoriaMetricsClient victoriaMetricsClient) {
		this.pluginCatalog = pluginCatalog;
		this.variableParser = variableParser;
		this.victoriaMetricsClient = victoriaMetricsClient;
	}

	public List<PluginResult> runAll(AnalysisRequest request, TimeRange timeRange) {
		List<TypedTarget> targets = variableParser.extractTypedTargets(request.getParameters());
		List<AnalysisPlugin> plugins = pluginCatalog.getPlugins();

		List<PlannedRun> planned = new ArrayList<>();
		for (TypedTarget target : targets) {
			for (AnalysisPlugin plugin : plugins) {
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

		log.info("Plugin runs finished: executed={}", results.size());
		return List.copyOf(results);
	}

	private void logPlannedRules(List<PlannedRun> planned) {
		if (planned.isEmpty()) {
			log.info("Matched rules to execute: count=0");
			return;
		}
		String formatted = planned.stream()
				.map(run -> run.plugin().name()
						+ " [" + run.plugin().targetTypePrefix() + "]"
						+ " <- " + run.target().canonicalName() + "=" + run.target().value())
				.collect(Collectors.joining("; "));
		log.info("Matched rules to execute: count={}, [{}]", planned.size(), formatted);
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
		log.info("VM parameterized query: rule='{}', param={}={}, query={}",
				plugin.name(), target.canonicalName(), target.value(), query);

		try {
			PrometheusResponse response = victoriaMetricsClient.query(query, timeRange.evaluationTimeSec());
			if (!isSuccess(response)) {
				String err = response == null ? "пустой ответ"
						: (response.getError() != null ? response.getError() : response.getStatus());
				log.info("VM request status: FAILED (rule='{}', param={})", plugin.name(), target.canonicalName());
				return PluginResult.skip(plugin, target, "VictoriaMetrics вернула ошибку: " + err);
			}

			List<Double> values = collectFiniteValues(response);
			if (values.isEmpty()) {
				log.info("VM request status: SUCCESS (rule='{}', param={}, datapoints=0)",
						plugin.name(), target.canonicalName());
				return PluginResult.noData(plugin, target, query);
			}

			double metricValue = values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
			boolean fail = values.stream().anyMatch(plugin.condition()::isViolation);
			log.info("VM request status: SUCCESS (rule='{}', param={}, datapoints={}, value={}, analysisFail={})",
					plugin.name(), target.canonicalName(), values.size(), metricValue, fail);
			return PluginResult.evaluated(plugin, target, query, metricValue, fail);
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

	/**
	 * {@code $VM} is filled with the concrete value of the current {@code VM_*} parameter.
	 */
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

	private static List<Double> collectFiniteValues(PrometheusResponse response) {
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

	private record PlannedRun(AnalysisPlugin plugin, TypedTarget target) {
	}
}
