package com.nla.NeuroLoadAnalyzer.plugin;

public enum PluginRunStatus {
	/** Condition not violated */
	OK,
	/** Condition violated (e.g. threshold exceeded) */
	VIOLATION,
	/** Required placeholders missing in the request */
	SKIPPED,
	/** Transport / query / parse failure */
	ERROR
}
