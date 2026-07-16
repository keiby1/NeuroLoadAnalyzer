package com.nla.NeuroLoadAnalyzer.plugin;

import com.nla.NeuroLoadAnalyzer.client.VictoriaMetricsClient;
import com.nla.NeuroLoadAnalyzer.dto.AnalysisRequest;
import com.nla.NeuroLoadAnalyzer.dto.PrometheusResponse;
import com.nla.NeuroLoadAnalyzer.service.RequestVariableParser;
import com.nla.NeuroLoadAnalyzer.util.TimeRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
		Map<String, String> variables = variableParser.canonicalVariables(request.getVariables());
		List<AnalysisPlugin> plugins = pluginCatalog.getPlugins();
		List<PluginResult> results = new ArrayList<>(plugins.size());

		for (AnalysisPlugin plugin : plugins) {
			results.add(runOne(plugin, variables, timeRange));
		}
		return List.copyOf(results);
	}

	private PluginResult runOne(AnalysisPlugin plugin, Map<String, String> variables, TimeRange timeRange) {
		PromQlBinder.BindResult bind = PromQlBinder.bind(
				plugin.promQlTemplate(),
				name -> variableParser.findValue(variables, name));

		if (!bind.complete()) {
			log.info("Plugin '{}' skipped, missing: {}", plugin.name(), bind.missingPlaceholders());
			return PluginResult.skipped(plugin, bind.missingPlaceholders());
		}

		String query = bind.boundQuery();
		try {
			PrometheusResponse response = victoriaMetricsClient.query(query, timeRange.evaluationTimeSec());
			Optional<Double> value = firstFiniteValue(response);
			if (value.isEmpty()) {
				return PluginResult.error(plugin, query, "VictoriaMetrics вернула пустой результат");
			}
			boolean violation = plugin.condition().isViolation(value.get());
			log.info("Plugin '{}' value={} violation={}", plugin.name(), value.get(), violation);
			return PluginResult.evaluated(plugin, query, value.get(), violation);
		} catch (RestClientException e) {
			log.warn("Plugin '{}' VM error: {}", plugin.name(), e.getMessage());
			return PluginResult.error(plugin, query, "Ошибка запроса к VictoriaMetrics: " + e.getMessage());
		} catch (RuntimeException e) {
			log.warn("Plugin '{}' failed: {}", plugin.name(), e.getMessage());
			return PluginResult.error(plugin, query, "Ошибка выполнения: " + e.getMessage());
		}
	}

	private static Optional<Double> firstFiniteValue(PrometheusResponse response) {
		if (response == null || response.getData() == null || response.getData().getResult() == null) {
			return Optional.empty();
		}
		if (!"success".equalsIgnoreCase(response.getStatus())) {
			return Optional.empty();
		}
		for (PrometheusResponse.Result result : response.getData().getResult()) {
			double value = VictoriaMetricsClient.parseValue(result.getValue());
			if (Double.isFinite(value)) {
				return Optional.of(value);
			}
		}
		return Optional.empty();
	}
}
