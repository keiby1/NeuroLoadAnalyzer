package com.nla.NeuroLoadAnalyzer.plugin;

import java.util.Locale;

/**
 * Two-boundary threshold: OK / soft (WARN|INFO) / FAIL.
 *
 * <p>Profile A (blocking soft): {@code ≤warn → OK}, {@code (warn; fail] → WARN}, {@code >fail → FAIL}.
 * <p>Profile B (info soft, exclusive hard): {@code ≤warn → OK}, {@code (warn; fail] → INFO}, {@code >fail → FAIL}.
 * <p>Profile B with inclusive hard (TCP): {@code ≤warn → OK}, {@code (warn; fail) → INFO}, {@code ≥fail → FAIL}.
 */
public record BandedThresholdCondition(
		double warnThreshold,
		double failThreshold,
		SoftBand softBand,
		HardBound hardBound
) implements AnalysisCondition {

	public enum SoftBand {
		WARN,
		INFO
	}

	public enum HardBound {
		/** FAIL when {@code value > failThreshold}; soft includes {@code value == failThreshold}. */
		EXCLUSIVE_GT,
		/** FAIL when {@code value >= failThreshold}; soft is {@code warn < value < fail}. */
		INCLUSIVE_GTE
	}

	public BandedThresholdCondition {
		if (softBand == null) {
			throw new IllegalArgumentException("softBand is required");
		}
		if (hardBound == null) {
			throw new IllegalArgumentException("hardBound is required");
		}
		if (failThreshold < warnThreshold) {
			throw new IllegalArgumentException("failThreshold must be >= warnThreshold");
		}
	}

	/** CPU/RAM: soft → WARN, hard {@code > fail}. */
	public static BandedThresholdCondition warnThenFail(double warnThreshold, double failThreshold) {
		return new BandedThresholdCondition(warnThreshold, failThreshold, SoftBand.WARN, HardBound.EXCLUSIVE_GT);
	}

	/** Throttle: soft → INFO, hard {@code > fail}. */
	public static BandedThresholdCondition infoThenFail(double warnThreshold, double failThreshold) {
		return new BandedThresholdCondition(warnThreshold, failThreshold, SoftBand.INFO, HardBound.EXCLUSIVE_GT);
	}

	/** TCP: soft → INFO, hard {@code >= fail}. */
	public static BandedThresholdCondition infoThenFailInclusive(double warnThreshold, double failThreshold) {
		return new BandedThresholdCondition(warnThreshold, failThreshold, SoftBand.INFO, HardBound.INCLUSIVE_GTE);
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
		if (value <= warnThreshold) {
			return new ThresholdVerdict(PluginRunStatus.OK,
					String.format(Locale.ROOT, "%.3f ≤ %.3f (OK)", value, warnThreshold));
		}
		boolean hardFail = switch (hardBound) {
			case EXCLUSIVE_GT -> value > failThreshold;
			case INCLUSIVE_GTE -> value >= failThreshold;
		};
		if (hardFail) {
			String tag = softBand == SoftBand.INFO ? "hard" : "fail";
			return new ThresholdVerdict(PluginRunStatus.FAIL,
					String.format(Locale.ROOT, "%.3f (%s)", value, tag));
		}
		PluginRunStatus softStatus = softBand == SoftBand.WARN ? PluginRunStatus.WARN : PluginRunStatus.INFO;
		String tag = softBand == SoftBand.INFO ? "soft" : "warn";
		return new ThresholdVerdict(softStatus,
				String.format(Locale.ROOT, "%.3f (%s)", value, tag));
	}

	@Override
	public String description() {
		String soft = softBand == SoftBand.WARN ? "WARN" : "INFO";
		String hard = hardBound == HardBound.INCLUSIVE_GTE ? ">=" : ">";
		return String.format(Locale.ROOT,
				"≤%.3f OK; soft→%s; %s%.3f FAIL",
				warnThreshold, soft, hard, failThreshold);
	}
}
