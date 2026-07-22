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
		String message,
		Double slopeBytesPerHour,
		Double slopePctPerHour,
		Double deltaAbsBytes,
		Double deltaPct
) {
	public static PluginResult skip(AnalysisPlugin plugin, TypedTarget target, String message) {
		return base(plugin, target, PluginRunStatus.SKIP, null, null, message, null);
	}

	public static PluginResult noData(AnalysisPlugin plugin, TypedTarget target, String boundQuery) {
		return base(plugin, target, PluginRunStatus.NO_DATA, boundQuery, null,
				"Запрос выполнен успешно, данных за выбранный диапазон нет", null);
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
				fail ? "Превышение порога" : "Превышения не было",
				null);
	}

	public static PluginResult fromSeries(
			AnalysisPlugin plugin,
			TypedTarget target,
			String boundQuery,
			SeriesVerdict verdict) {
		Double display = verdict.slopePctPerHour() != null
				? verdict.slopePctPerHour()
				: verdict.deltaPct();
		return base(plugin, target, verdict.status(), boundQuery, display, verdict.reason(), verdict);
	}

	/** K8S workload check: hierarchy software=namespace, purpose=deployment. */
	public static PluginResult evaluatedK8s(
			AnalysisPlugin plugin,
			String namespace,
			String deploymentName,
			String workloadType,
			String boundQuery,
			double metricValue,
			boolean fail) {
		PluginRunStatus status = fail ? PluginRunStatus.FAIL : PluginRunStatus.OK;
		String message = fail ? "Превышение порога" : "Превышения не было";
		return new PluginResult(
				plugin.name(),
				"K8S",
				namespace,
				deploymentName,
				workloadType,
				"",
				status,
				plugin.promQlTemplate(),
				boundQuery,
				metricValue,
				plugin.conditionDescription(),
				message,
				null,
				null,
				null,
				null);
	}

	public static PluginResult skipK8s(
			AnalysisPlugin plugin,
			String namespace,
			String deploymentName,
			String message) {
		return new PluginResult(
				plugin.name(),
				"K8S",
				namespace,
				deploymentName,
				"",
				"",
				PluginRunStatus.SKIP,
				plugin.promQlTemplate(),
				null,
				null,
				plugin.conditionDescription(),
				message,
				null,
				null,
				null,
				null);
	}

	public static PluginResult noDataK8s(
			AnalysisPlugin plugin,
			String namespace,
			String deploymentName,
			String boundQuery) {
		return new PluginResult(
				plugin.name(),
				"K8S",
				namespace,
				deploymentName,
				"",
				"",
				PluginRunStatus.NO_DATA,
				plugin.promQlTemplate(),
				boundQuery,
				null,
				plugin.conditionDescription(),
				"Нет данных по утилизации контейнеров",
				null,
				null,
				null,
				null);
	}

	public static PluginResult fromSeriesK8s(
			AnalysisPlugin plugin,
			String namespace,
			String deploymentName,
			String workloadType,
			String boundQuery,
			SeriesVerdict verdict) {
		Double display = verdict.slopePctPerHour() != null
				? verdict.slopePctPerHour()
				: verdict.deltaPct();
		return new PluginResult(
				plugin.name(),
				"K8S",
				namespace,
				deploymentName,
				workloadType,
				"",
				verdict.status(),
				plugin.promQlTemplate(),
				boundQuery,
				display,
				plugin.conditionDescription(),
				verdict.reason(),
				verdict.slopeBytesPerHour(),
				verdict.slopePctPerHour(),
				verdict.deltaAbsBytes(),
				verdict.deltaPct());
	}

	private static PluginResult base(
			AnalysisPlugin plugin,
			TypedTarget target,
			PluginRunStatus status,
			String boundQuery,
			Double value,
			String message,
			SeriesVerdict verdict) {
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
				plugin.conditionDescription(),
				message,
				verdict == null ? null : verdict.slopeBytesPerHour(),
				verdict == null ? null : verdict.slopePctPerHour(),
				verdict == null ? null : verdict.deltaAbsBytes(),
				verdict == null ? null : verdict.deltaPct());
	}
}
