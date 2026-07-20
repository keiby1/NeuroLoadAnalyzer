package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * How the plugin fetches data from VictoriaMetrics.
 */
public enum QueryMode {
	/** Instant {@code /api/v1/query} + scalar threshold */
	INSTANT,
	/** Range {@code /api/v1/query_range} + series/trend analysis */
	RANGE
}
