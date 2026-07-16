package com.nla.NeuroLoadAnalyzer.plugin;

import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;

public record PluginResult(
		String pluginName,
		String targetType,
		String software,
		String purpose,
		String parameterName,
		String parameterValue,
		PluginRunStatus status,
		String promQlTemplate,
		String boundQuery,
		Double metricValue,
		String conditionDescription,
		String message
) {
	public static PluginResult skip(AnalysisPlugin plugin, TypedTarget target, String message) {
		return base(plugin, target, PluginRunStatus.SKIP, null, null, message);
	}

	public static PluginResult noData(AnalysisPlugin plugin, TypedTarget target, String boundQuery) {
		return base(plugin, target, PluginRunStatus.NO_DATA, boundQuery, null,
				"Запрос выполнен успешно, данных за выбранный диапазон нет");
	}

	public static PluginResult evaluated(
			AnalysisPlugin plugin,
			TypedTarget target,
			String boundQuery,
			double value,
			boolean fail) {
		return base(
				plugin,
				target,
				fail ? PluginRunStatus.FAIL : PluginRunStatus.OK,
				boundQuery,
				value,
				fail ? "Превышение порога" : "Превышения не было");
	}

	private static PluginResult base(
			AnalysisPlugin plugin,
			TypedTarget target,
			PluginRunStatus status,
			String boundQuery,
			Double value,
			String message) {
		return new PluginResult(
				plugin.name(),
				target.type(),
				target.software(),
				target.purpose(),
				target.canonicalName(),
				target.value(),
				status,
				plugin.promQlTemplate(),
				boundQuery,
				value,
				plugin.condition().description(),
				message);
	}
}
