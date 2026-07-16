package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * Check condition evaluated against a metric value from VictoriaMetrics.
 * Describes when the rule is considered violated (e.g. CPU &gt; 80).
 */
public interface AnalysisCondition {

	/**
	 * @return {@code true} if the value violates the rule
	 */
	boolean isViolation(double value);

	String description();
}
