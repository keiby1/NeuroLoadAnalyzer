package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * Analysis rule (plugin): name, PromQL template, and violation condition.
 *
 * <p>PromQL placeholders use {@code $ParamName} syntax, e.g. {@code $VM_Kafka_GW}.
 */
public record AnalysisPlugin(
		String name,
		String promQlTemplate,
		AnalysisCondition condition
) {
	public AnalysisPlugin {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("plugin name is required");
		}
		if (promQlTemplate == null || promQlTemplate.isBlank()) {
			throw new IllegalArgumentException("promQlTemplate is required");
		}
		if (condition == null) {
			throw new IllegalArgumentException("condition is required");
		}
	}
}
