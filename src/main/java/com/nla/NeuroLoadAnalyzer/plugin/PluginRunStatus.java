package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * User-facing analysis statuses for the HTML report.
 */
public enum PluginRunStatus {
	/** Threshold not exceeded / no leak / stable trend */
	OK,
	/** Soft signal: band gray zone (blocking), leak suspicion */
	WARN,
	/** Soft advisory band (throttle/TCP) or insufficient trend data */
	INFO,
	/** Threshold exceeded or confirmed leak / growing trend */
	FAIL,
	/** Query succeeded but returned no datapoints */
	NO_DATA,
	/** Any error that prevented obtaining data */
	SKIP
}
