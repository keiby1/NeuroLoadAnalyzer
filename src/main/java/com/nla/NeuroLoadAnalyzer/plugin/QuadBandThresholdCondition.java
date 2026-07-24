package com.nla.NeuroLoadAnalyzer.plugin;

import java.util.Locale;

/**
 * Four-band threshold: OK / INFO / WARN / FAIL.
 *
 * <p>Example (throttle %): {@code ≤okMax → OK}, {@code (okMax; infoMax] → INFO},
 * {@code (infoMax; warnMax] → WARN}, {@code >warnMax → FAIL}.
 */
public record QuadBandThresholdCondition(
		double okMax,
		double infoMax,
		double warnMax
) implements AnalysisCondition {

	public QuadBandThresholdCondition {
		if (!(okMax <= infoMax && infoMax <= warnMax)) {
			throw new IllegalArgumentException("require okMax ≤ infoMax ≤ warnMax");
		}
	}

	/** Throttle: ≤1 OK · (1; 3] INFO · (3; 7] WARN · >7 FAIL. */
	public static QuadBandThresholdCondition infoWarnThenFail(double okMax, double infoMax, double warnMax) {
		return new QuadBandThresholdCondition(okMax, infoMax, warnMax);
	}

	@Override
	public boolean isViolation(double value) {
		return evaluate(value).status() == PluginRunStatus.FAIL;
	}

	@Override
	public ThresholdVerdict evaluate(double value) {
		if (!Double.isFinite(value)) {
			return new ThresholdVerdict(PluginRunStatus.NO_DATA, "Некорректное значение метрики");
		}
		if (value <= okMax) {
			return new ThresholdVerdict(PluginRunStatus.OK,
					String.format(Locale.ROOT, "%.3f ≤ %.3f (OK)", value, okMax));
		}
		if (value <= infoMax) {
			return new ThresholdVerdict(PluginRunStatus.INFO,
					String.format(Locale.ROOT, "%.3f (soft/info)", value));
		}
		if (value <= warnMax) {
			return new ThresholdVerdict(PluginRunStatus.WARN,
					String.format(Locale.ROOT, "%.3f (warn)", value));
		}
		return new ThresholdVerdict(PluginRunStatus.FAIL,
				String.format(Locale.ROOT, "%.3f (hard)", value));
	}

	@Override
	public String description() {
		return String.format(Locale.ROOT,
				"≤%.3f OK; (%.3f; %.3f] INFO; (%.3f; %.3f] WARN; >%.3f FAIL",
				okMax, okMax, infoMax, infoMax, warnMax, warnMax);
	}
}
