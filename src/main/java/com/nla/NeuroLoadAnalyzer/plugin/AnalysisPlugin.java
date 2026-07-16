package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * Analysis rule (plugin): applies to all request params with matching type prefix
 * (e.g. {@code targetTypePrefix=VM} → {@code VM_Kafka_GW}, {@code VM_Postgre_ASD}).
 *
 * <p>PromQL placeholders use {@code $} + type prefix, e.g. {@code $VM} — substituted with
 * the concrete parameter value for each matched target.
 */
public record AnalysisPlugin(
		String name,
		String targetTypePrefix,
		String promQlTemplate,
		AnalysisCondition condition
) {
	public AnalysisPlugin {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("plugin name is required");
		}
		if (targetTypePrefix == null || targetTypePrefix.isBlank()) {
			throw new IllegalArgumentException("targetTypePrefix is required");
		}
		if (promQlTemplate == null || promQlTemplate.isBlank()) {
			throw new IllegalArgumentException("promQlTemplate is required");
		}
		if (condition == null) {
			throw new IllegalArgumentException("condition is required");
		}
		targetTypePrefix = targetTypePrefix.trim();
	}

	public boolean appliesTo(String type) {
		return type != null && targetTypePrefix.equalsIgnoreCase(type.trim());
	}
}
