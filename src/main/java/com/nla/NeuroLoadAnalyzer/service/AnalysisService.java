package com.nla.NeuroLoadAnalyzer.service;

import com.nla.NeuroLoadAnalyzer.config.VictoriaMetricsProperties;
import com.nla.NeuroLoadAnalyzer.dto.AnalysisReport;
import com.nla.NeuroLoadAnalyzer.dto.AnalysisRequest;
import com.nla.NeuroLoadAnalyzer.dto.K8sNamespaceTarget;
import com.nla.NeuroLoadAnalyzer.dto.NamedParameter;
import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPluginCatalog;
import com.nla.NeuroLoadAnalyzer.plugin.PluginAnalysisService;
import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.plugin.catalog.ExamplePluginCatalog;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.TypeReportGroup;
import com.nla.NeuroLoadAnalyzer.util.TimeRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
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
		List<K8sNamespaceTarget> k8sNamespaces = requestVariableParser.extractK8sNamespaces(request.getParameters());
		logVmPrefixParameters(typedTargets);
		logK8sNamespaces(k8sNamespaces);

		List<PluginResult> pluginResults = pluginAnalysisService.runAll(request, timeRange);
		List<TypeReportGroup> typeGroups = ReportTreeBuilder.build(pluginResults);
		String catalogSource = pluginCatalog instanceof ExamplePluginCatalog
				? "ExamplePluginCatalog"
				: pluginCatalog.getClass().getSimpleName();

		log.info("Analysis complete: typedTargets={}, k8sNamespaces={}, pluginRuns={}, typeGroups={}, catalog={}",
				typedTargets.size(), k8sNamespaces.size(), pluginResults.size(), typeGroups.size(), catalogSource);

		AnalysisReport report = new AnalysisReport(
				timeRange,
				typedTargets,
				pluginResults,
				typeGroups,
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

	private void logK8sNamespaces(List<K8sNamespaceTarget> namespaces) {
		String formatted = namespaces.stream()
				.map(K8sNamespaceTarget::namespace)
				.collect(Collectors.joining(", "));
		log.info("k8s_ namespace parameters: count={}, [{}]", namespaces.size(), formatted);
	}
}
