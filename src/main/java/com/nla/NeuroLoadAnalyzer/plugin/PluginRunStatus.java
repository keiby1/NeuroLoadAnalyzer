package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * User-facing analysis statuses for the HTML report.
 */
public enum PluginRunStatus {
	/** Threshold not exceeded / no leak */
	OK,
	/** Soft signal: suspicion, insufficient horizon, short window */
	WARN,
	/** Threshold exceeded or confirmed leak */
	FAIL,
	/** Query succeeded but returned no datapoints */
	NO_DATA,
	/** Any error that prevented obtaining data */
	SKIP
}
