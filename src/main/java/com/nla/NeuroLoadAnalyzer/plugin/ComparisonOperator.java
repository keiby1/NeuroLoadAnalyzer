package com.nla.NeuroLoadAnalyzer.plugin;

public enum ComparisonOperator {
	GT(">"),
	GTE(">="),
	LT("<"),
	LTE("<=");

	private final String symbol;

	ComparisonOperator(String symbol) {
		this.symbol = symbol;
	}

	public String symbol() {
		return symbol;
	}

	public boolean matches(double value, double threshold) {
		return switch (this) {
			case GT -> value > threshold;
			case GTE -> value >= threshold;
			case LT -> value < threshold;
			case LTE -> value <= threshold;
		};
	}
}
