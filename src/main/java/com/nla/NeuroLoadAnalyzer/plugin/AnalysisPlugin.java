package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * Analysis rule (plugin): applies to all request params with matching type prefix
 * (e.g. {@code targetTypePrefix=VM} → {@code VM_Kafka_GW}).
 *
 * <p>PromQL placeholders use {@code $} + type prefix, e.g. {@code $VM}.
 * INSTANT plugins use {@link AnalysisCondition}; RANGE plugins use {@link SeriesAnalysisCondition}.
 * K8S inventory checks use {@link WorkloadMetric} against pre-fetched workloads.
 */
public record AnalysisPlugin(
		String name,
		String targetTypePrefix,
		QueryMode queryMode,
		String promQlTemplate,
		AnalysisCondition thresholdCondition,
		SeriesAnalysisCondition seriesCondition,
		int stepMinutes,
		WorkloadMetric workloadMetric
) {
	public AnalysisPlugin {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("plugin name is required");
		}
		if (targetTypePrefix == null || targetTypePrefix.isBlank()) {
			throw new IllegalArgumentException("targetTypePrefix is required");
		}
		if (queryMode == null) {
			throw new IllegalArgumentException("queryMode is required");
		}
		if (promQlTemplate == null || promQlTemplate.isBlank()) {
			throw new IllegalArgumentException("promQlTemplate is required");
		}
		if (workloadMetric == null) {
			workloadMetric = WorkloadMetric.NONE;
		}
		if (queryMode == QueryMode.INSTANT
				&& workloadMetric == WorkloadMetric.NONE
				&& thresholdCondition == null) {
			throw new IllegalArgumentException("thresholdCondition is required for INSTANT plugins");
		}
		if (queryMode == QueryMode.RANGE && seriesCondition == null) {
			throw new IllegalArgumentException("seriesCondition is required for RANGE plugins");
		}
		if (stepMinutes <= 0) {
			stepMinutes = 5;
		}
		targetTypePrefix = targetTypePrefix.trim();
	}

	/** Backward-compatible factory for threshold (instant) VM plugins. */
	public AnalysisPlugin(String name, String targetTypePrefix, String promQlTemplate, AnalysisCondition condition) {
		this(name, targetTypePrefix, QueryMode.INSTANT, promQlTemplate, condition, null, 5, WorkloadMetric.NONE);
	}

	public static AnalysisPlugin range(
			String name,
			String targetTypePrefix,
			String promQlTemplate,
			SeriesAnalysisCondition seriesCondition,
			int stepMinutes) {
		return new AnalysisPlugin(
				name, targetTypePrefix, QueryMode.RANGE, promQlTemplate, null, seriesCondition, stepMinutes,
				WorkloadMetric.NONE);
	}

	public static AnalysisPlugin k8sThreshold(
			String name,
			String documentedPromQl,
			AnalysisCondition condition,
			WorkloadMetric metric) {
		return new AnalysisPlugin(
				name, "K8S", QueryMode.INSTANT, documentedPromQl, condition, null, 5, metric);
	}

	/** K8S RANGE check against a pre-fetched workload series ({@link WorkloadMetric}). */
	public static AnalysisPlugin k8sSeries(
			String name,
			String documentedPromQl,
			SeriesAnalysisCondition seriesCondition,
			WorkloadMetric metric,
			int stepMinutes) {
		return new AnalysisPlugin(
				name, "K8S", QueryMode.RANGE, documentedPromQl, null, seriesCondition, stepMinutes, metric);
	}

	public boolean appliesTo(String type) {
		return type != null && targetTypePrefix.equalsIgnoreCase(type.trim());
	}

	public boolean isK8sWorkloadCheck() {
		return workloadMetric != WorkloadMetric.NONE;
	}

	public String conditionDescription() {
		if (queryMode == QueryMode.RANGE && seriesCondition != null) {
			return seriesCondition.description();
		}
		if (thresholdCondition != null) {
			return thresholdCondition.description();
		}
		return "";
	}
}
