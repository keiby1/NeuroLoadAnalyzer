package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * Verdict of a scalar threshold / banded check.
 */
public record ThresholdVerdict(PluginRunStatus status, String reason) {
	public ThresholdVerdict {
		if (status == null) {
			throw new IllegalArgumentException("status is required");
		}
		if (reason == null) {
			reason = "";
		}
	}
}
