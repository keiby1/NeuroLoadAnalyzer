package com.nla.NeuroLoadAnalyzer.service;

import com.nla.NeuroLoadAnalyzer.config.VictoriaMetricsProperties;
import com.nla.NeuroLoadAnalyzer.dto.AnalysisReport;
import com.nla.NeuroLoadAnalyzer.dto.AnalysisRequest;
import com.nla.NeuroLoadAnalyzer.dto.NamedParameter;
import com.nla.NeuroLoadAnalyzer.dto.SoftwareReportGroup;
import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPluginCatalog;
import com.nla.NeuroLoadAnalyzer.plugin.PluginAnalysisService;
import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.plugin.catalog.ExamplePluginCatalog;
import com.nla.NeuroLoadAnalyzer.util.TimeRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates request parsing → plugin analysis → HTML report.
 */
@Service
public class AnalysisService {

	private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

	private final VictoriaMetricsProperties victoriaMetricsProperties;
	private final AnalysisPageService analysisPageService;
	private final RequestVariableParser requestVariableParser;
	private final PluginAnalysisService pluginAnalysisService;
	private final AnalysisPluginCatalog pluginCatalog;

	public AnalysisService(
			VictoriaMetricsProperties victoriaMetricsProperties,
			AnalysisPageService analysisPageService,
			RequestVariableParser requestVariableParser,
			PluginAnalysisService pluginAnalysisService,
			AnalysisPluginCatalog pluginCatalog) {
		this.victoriaMetricsProperties = victoriaMetricsProperties;
		this.analysisPageService = analysisPageService;
		this.requestVariableParser = requestVariableParser;
		this.pluginAnalysisService = pluginAnalysisService;
		this.pluginCatalog = pluginCatalog;
	}

	public String analyze(AnalysisRequest request) {
		TimeRange timeRange = TimeRange.of(
				request.getFromMs(),
				request.getToMs(),
				victoriaMetricsProperties.getTimeRange());

		logIncomingParameters(request);
		List<TypedTarget> typedTargets = requestVariableParser.extractTypedTargets(request.getParameters());
		logVmPrefixParameters(typedTargets);

		List<PluginResult> pluginResults = pluginAnalysisService.runAll(request, timeRange);
		List<SoftwareReportGroup> groups = groupBySoftware(pluginResults);
		String catalogSource = pluginCatalog instanceof ExamplePluginCatalog
				? "ExamplePluginCatalog"
				: pluginCatalog.getClass().getSimpleName();

		log.info("Analysis complete: typedTargets={}, pluginRuns={}, softwareGroups={}, catalog={}",
				typedTargets.size(), pluginResults.size(), groups.size(), catalogSource);

		AnalysisReport report = new AnalysisReport(
				timeRange,
				typedTargets,
				pluginResults,
				groups,
				catalogSource);

		return analysisPageService.renderReport(report);
	}

	private void logIncomingParameters(AnalysisRequest request) {
		List<NamedParameter> parameters = request.getParameters();
		String formatted = parameters.stream()
				.map(p -> p.name() + "=" + p.value())
				.collect(Collectors.joining(", "));
		log.info("Incoming parameters (from={}, to={}): count={}, [{}]",
				request.getFromMs(),
				request.getToMs(),
				parameters.size(),
				formatted);
	}

	private void logVmPrefixParameters(List<TypedTarget> typedTargets) {
		List<TypedTarget> vmTargets = typedTargets.stream()
				.filter(t -> "VM".equalsIgnoreCase(t.type()))
				.toList();
		String formatted = vmTargets.stream()
				.map(t -> t.canonicalName() + "=" + t.value())
				.collect(Collectors.joining(", "));
		log.info("VM_ prefix parameters: count={}, [{}]", vmTargets.size(), formatted);
	}

	static List<SoftwareReportGroup> groupBySoftware(List<PluginResult> results) {
		Map<String, List<PluginResult>> bySoftware = new LinkedHashMap<>();
		for (PluginResult result : results) {
			String software = result.software() == null || result.software().isBlank()
					? "unknown"
					: result.software();
			bySoftware.computeIfAbsent(software, key -> new ArrayList<>()).add(result);
		}
		List<SoftwareReportGroup> groups = new ArrayList<>();
		for (Map.Entry<String, List<PluginResult>> entry : bySoftware.entrySet()) {
			groups.add(new SoftwareReportGroup(entry.getKey(), List.copyOf(entry.getValue())));
		}
		return List.copyOf(groups);
	}
}
