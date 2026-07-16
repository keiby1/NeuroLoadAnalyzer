package com.nla.NeuroLoadAnalyzer.dto;

import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.util.TimeRange;

import java.util.List;
import java.util.Map;

/**
 * Intermediate analysis payload used for HTML rendering.
 */
public record AnalysisReport(
		TimeRange timeRange,
		Map<String, String> variables,
		List<TypedTarget> typedTargets,
		List<PluginResult> pluginResults,
		String catalogSource
) {
}
