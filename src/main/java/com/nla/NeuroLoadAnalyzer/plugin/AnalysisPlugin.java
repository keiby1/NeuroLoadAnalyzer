package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * Analysis rule (plugin): applies to all request params with matching type prefix
 * (e.g. {@code targetTypePrefix=VM} → {@code VM_Kafka_GW}, {@code VM_Postgre_ASD}).
 *
 * <p>PromQL placeholders use {@code $} + type prefix, e.g. {@code $VM}.
 * INSTANT plugins use {@link AnalysisCondition}; RANGE plugins use {@link SeriesAnalysisCondition}.
 */
public record AnalysisPlugin(
		String name,
		String targetTypePrefix,
		QueryMode queryMode,
		String promQlTemplate,
		AnalysisCondition thresholdCondition,
		SeriesAnalysisCondition seriesCondition,
		int stepMinutes
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
		if (queryMode == QueryMode.INSTANT && thresholdCondition == null) {
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

	/** Backward-compatible factory for threshold (instant) plugins. */
	public AnalysisPlugin(String name, String targetTypePrefix, String promQlTemplate, AnalysisCondition condition) {
		this(name, targetTypePrefix, QueryMode.INSTANT, promQlTemplate, condition, null, 5);
	}

	public static AnalysisPlugin range(
			String name,
			String targetTypePrefix,
			String promQlTemplate,
			SeriesAnalysisCondition seriesCondition,
			int stepMinutes) {
		return new AnalysisPlugin(
				name, targetTypePrefix, QueryMode.RANGE, promQlTemplate, null, seriesCondition, stepMinutes);
	}

	public boolean appliesTo(String type) {
		return type != null && targetTypePrefix.equalsIgnoreCase(type.trim());
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
