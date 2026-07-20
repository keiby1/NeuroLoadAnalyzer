package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * One datapoint from a VictoriaMetrics range series.
 *
 * @param timestampSec Unix seconds
 * @param value        metric value
 */
public record MetricPoint(long timestampSec, double value) {
}
