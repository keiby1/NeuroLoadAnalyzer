package com.nla.NeuroLoadAnalyzer.dto;

import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;

import java.util.List;

/**
 * Report section: metrics for one software kind (e.g. Kafka, Postgre).
 */
public record SoftwareReportGroup(
		String software,
		List<PluginResult> results
) {
}
