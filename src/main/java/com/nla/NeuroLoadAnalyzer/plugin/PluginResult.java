package com.nla.NeuroLoadAnalyzer.plugin;

import java.util.List;

public record PluginResult(
		String pluginName,
		PluginRunStatus status,
		String promQlTemplate,
		String boundQuery,
		List<String> missingPlaceholders,
		Double metricValue,
		String conditionDescription,
		String message
) {
	public static PluginResult skipped(AnalysisPlugin plugin, List<String> missing) {
		return new PluginResult(
				plugin.name(),
				PluginRunStatus.SKIPPED,
				plugin.promQlTemplate(),
				null,
				missing,
				null,
				plugin.condition().description(),
				"Не хватает параметров: " + String.join(", ", missing));
	}

	public static PluginResult error(AnalysisPlugin plugin, String boundQuery, String message) {
		return new PluginResult(
				plugin.name(),
				PluginRunStatus.ERROR,
				plugin.promQlTemplate(),
				boundQuery,
				List.of(),
				null,
				plugin.condition().description(),
				message);
	}

	public static PluginResult evaluated(
			AnalysisPlugin plugin,
			String boundQuery,
			double value,
			boolean violation) {
		return new PluginResult(
				plugin.name(),
				violation ? PluginRunStatus.VIOLATION : PluginRunStatus.OK,
				plugin.promQlTemplate(),
				boundQuery,
				List.of(),
				value,
				plugin.condition().description(),
				violation ? "Условие нарушения выполнено" : "OK");
	}
}
