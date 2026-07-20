package com.nla.NeuroLoadAnalyzer.plugin;

import java.util.List;

/**
 * Condition evaluated on a time series (RANGE plugins), e.g. RAM leak detection.
 */
public interface SeriesAnalysisCondition {

	SeriesVerdict evaluate(List<MetricPoint> series);

	String description();
}
