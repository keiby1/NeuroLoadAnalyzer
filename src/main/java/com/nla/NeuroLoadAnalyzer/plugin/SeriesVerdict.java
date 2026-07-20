package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * Verdict of a series/trend analysis with evidence for the report UI.
 */
public record SeriesVerdict(
		PluginRunStatus status,
		String reason,
		Double slopeBytesPerHour,
		Double slopePctPerHour,
		Double deltaAbsBytes,
		Double deltaPct,
		Double mannKendallPValue,
		Double senSlopeBytesPerSec
) {
	public static SeriesVerdict of(PluginRunStatus status, String reason) {
		return new SeriesVerdict(status, reason, null, null, null, null, null, null);
	}

	public SeriesVerdict withEvidence(
			Double slopeBytesPerHour,
			Double slopePctPerHour,
			Double deltaAbsBytes,
			Double deltaPct,
			Double mannKendallPValue,
			Double senSlopeBytesPerSec) {
		return new SeriesVerdict(
				status,
				reason,
				slopeBytesPerHour,
				slopePctPerHour,
				deltaAbsBytes,
				deltaPct,
				mannKendallPValue,
				senSlopeBytesPerSec);
	}
}
