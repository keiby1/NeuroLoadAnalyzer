package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * Threshold-based condition, e.g. "value &gt; 80".
 */
public record ThresholdCondition(
		ComparisonOperator operator,
		double threshold
) implements AnalysisCondition {

	public ThresholdCondition {
		if (operator == null) {
			throw new IllegalArgumentException("operator is required");
		}
	}

	public static ThresholdCondition greaterThan(double threshold) {
		return new ThresholdCondition(ComparisonOperator.GT, threshold);
	}

	public static ThresholdCondition greaterOrEqual(double threshold) {
		return new ThresholdCondition(ComparisonOperator.GTE, threshold);
	}

	public static ThresholdCondition lessThan(double threshold) {
		return new ThresholdCondition(ComparisonOperator.LT, threshold);
	}

	public static ThresholdCondition lessOrEqual(double threshold) {
		return new ThresholdCondition(ComparisonOperator.LTE, threshold);
	}

	@Override
	public boolean isViolation(double value) {
		return operator.matches(value, threshold);
	}

	@Override
	public String description() {
		return "value " + operator.symbol() + " " + threshold;
	}
}
