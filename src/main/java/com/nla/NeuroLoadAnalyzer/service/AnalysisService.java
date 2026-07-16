package com.nla.NeuroLoadAnalyzer.service;

import com.nla.NeuroLoadAnalyzer.config.VictoriaMetricsProperties;
import com.nla.NeuroLoadAnalyzer.dto.AnalysisReport;
import com.nla.NeuroLoadAnalyzer.dto.AnalysisRequest;
import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPluginCatalog;
import com.nla.NeuroLoadAnalyzer.plugin.PluginAnalysisService;
import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.plugin.catalog.ExamplePluginCatalog;
import com.nla.NeuroLoadAnalyzer.util.TimeRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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

		Map<String, String> variables = requestVariableParser.canonicalVariables(request.getVariables());
		List<TypedTarget> typedTargets = requestVariableParser.extractTypedTargets(request.getVariables());
		List<PluginResult> pluginResults = pluginAnalysisService.runAll(request, timeRange);
		String catalogSource = pluginCatalog instanceof ExamplePluginCatalog
				? "ExamplePluginCatalog"
				: pluginCatalog.getClass().getSimpleName();

		log.info("Analysis complete: typedTargets={}, plugins={}, catalog={}",
				typedTargets.size(), pluginResults.size(), catalogSource);

		AnalysisReport report = new AnalysisReport(
				timeRange,
				variables,
				typedTargets,
				pluginResults,
				catalogSource);

		return analysisPageService.renderReport(report);
	}
}
