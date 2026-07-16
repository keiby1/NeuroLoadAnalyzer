package com.nla.NeuroLoadAnalyzer.dto;

import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.TypeReportGroup;
import com.nla.NeuroLoadAnalyzer.util.TimeRange;

import java.util.List;

/**
 * Intermediate analysis payload used for HTML rendering.
 */
public record AnalysisReport(
		TimeRange timeRange,
		List<TypedTarget> typedTargets,
		List<PluginResult> pluginResults,
		List<TypeReportGroup> typeGroups,
		String catalogSource
) {
}
