package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * User-facing analysis statuses for the HTML report.
 */
public enum PluginRunStatus {
	/** Threshold not exceeded */
	OK,
	/** Threshold exceeded (Fail) */
	FAIL,
	/** Query succeeded but returned no datapoints */
	NO_DATA,
	/** Any error that prevented obtaining data */
	SKIP
}
