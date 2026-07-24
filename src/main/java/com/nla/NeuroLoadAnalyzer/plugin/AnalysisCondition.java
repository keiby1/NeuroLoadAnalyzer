package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * Check condition evaluated against a metric value from VictoriaMetrics.
 */
public interface AnalysisCondition {

	/**
	 * @return {@code true} if the value is in the hard-FAIL band (or single-threshold violation)
	 */
	boolean isViolation(double value);

	/**
	 * Full verdict including soft bands (WARN / INFO).
	 */
	default ThresholdVerdict evaluate(double value) {
		if (isViolation(value)) {
			return new ThresholdVerdict(PluginRunStatus.FAIL, "Превышение порога");
		}
		return new ThresholdVerdict(PluginRunStatus.OK, "Превышения не было");
	}

	String description();
}
