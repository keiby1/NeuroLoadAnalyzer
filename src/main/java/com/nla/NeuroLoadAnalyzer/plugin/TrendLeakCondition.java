package com.nla.NeuroLoadAnalyzer.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Detects sustained memory growth (leak) on a RANGE series of used RAM bytes.
 *
 * <p>Thresholds are starting defaults for ~12h load tests and should be calibrated on real runs.
 * See {@code docs/DEV_NOTES.md}.
 */
public final class TrendLeakCondition implements SeriesAnalysisCondition {

	public static final double DEFAULT_WARMUP_HOURS = 1.0;
	public static final double DEFAULT_MIN_ANALYZE_HOURS = 4.0;
	public static final double DEFAULT_SLOPE_WARN_PCT_PER_HOUR = 0.05;
	public static final double DEFAULT_SLOPE_FAIL_PCT_PER_HOUR = 0.20;
	public static final long DEFAULT_MIN_ABS_GROWTH_BYTES = 75L * 1024L * 1024L; // 75 MiB
	public static final double DEFAULT_SIGNIFICANCE_ALPHA = 0.05;
	public static final double DEFAULT_LATE_ONSET_TAIL_HOURS = 2.0;
	public static final int DEFAULT_MIN_POINTS = 12;

	private final double warmupHours;
	private final double minAnalyzeHours;
	private final double slopeWarnPctPerHour;
	private final double slopeFailPctPerHour;
	private final long minAbsGrowthBytes;
	private final double significanceAlpha;
	private final double lateOnsetTailHours;
	private final int minPoints;

	public TrendLeakCondition() {
		this(
				DEFAULT_WARMUP_HOURS,
				DEFAULT_MIN_ANALYZE_HOURS,
				DEFAULT_SLOPE_WARN_PCT_PER_HOUR,
				DEFAULT_SLOPE_FAIL_PCT_PER_HOUR,
				DEFAULT_MIN_ABS_GROWTH_BYTES,
				DEFAULT_SIGNIFICANCE_ALPHA,
				DEFAULT_LATE_ONSET_TAIL_HOURS,
				DEFAULT_MIN_POINTS);
	}

	public TrendLeakCondition(
			double warmupHours,
			double minAnalyzeHours,
			double slopeWarnPctPerHour,
			double slopeFailPctPerHour,
			long minAbsGrowthBytes,
			double significanceAlpha,
			double lateOnsetTailHours,
			int minPoints) {
		this.warmupHours = warmupHours;
		this.minAnalyzeHours = minAnalyzeHours;
		this.slopeWarnPctPerHour = slopeWarnPctPerHour;
		this.slopeFailPctPerHour = slopeFailPctPerHour;
		this.minAbsGrowthBytes = minAbsGrowthBytes;
		this.significanceAlpha = significanceAlpha;
		this.lateOnsetTailHours = lateOnsetTailHours;
		this.minPoints = minPoints;
	}

	public static TrendLeakCondition defaults() {
		return new TrendLeakCondition();
	}

	@Override
	public String description() {
		return String.format(Locale.ROOT,
				"RAM growth/leak: Sen+MK, warn≥%.2f%%/h, fail≥%.2f%%/h (calibrate on 12h runs)",
				slopeWarnPctPerHour, slopeFailPctPerHour);
	}

	@Override
	public SeriesVerdict evaluate(List<MetricPoint> series) {
		if (series == null || series.isEmpty()) {
			return SeriesVerdict.of(PluginRunStatus.NO_DATA, "Пустой ряд метрик");
		}

		List<MetricPoint> sorted = series.stream()
				.filter(p -> p != null && Double.isFinite(p.value()))
				.sorted((a, b) -> Long.compare(a.timestampSec(), b.timestampSec()))
				.toList();
		if (sorted.isEmpty()) {
			return SeriesVerdict.of(PluginRunStatus.NO_DATA, "Нет конечных точек в ряде");
		}

		long t0 = sorted.get(0).timestampSec();
		long warmupEnd = t0 + Math.round(warmupHours * 3600.0);
		List<MetricPoint> analyzed = dropBefore(sorted, warmupEnd);

		if (analyzed.size() < minPoints || durationHours(analyzed) < minAnalyzeHours) {
			return evidence(
					SeriesVerdict.of(PluginRunStatus.WARN,
							String.format(Locale.ROOT,
									"Мало данных для детекции утечки (точек=%d, окно=%.2fч после warmup %.1fч; нужно ≥%d точек и ≥%.1fч)",
									analyzed.size(), durationHours(analyzed), warmupHours, minPoints, minAnalyzeHours)),
					analyzed, null, 0, 0, 0, 0);
		}

		TrendStatistics.FitResult fit = TrendStatistics.fit(analyzed, significanceAlpha);
		double baseline = analyzed.get(0).value();
		if (baseline <= 0) {
			baseline = medianFirst(analyzed, Math.min(6, analyzed.size()));
		}
		if (baseline <= 0) {
			return SeriesVerdict.of(PluginRunStatus.WARN, "Некорректный baseline (≤0), анализ роста невозможен");
		}

		double slopeBytesPerHour = fit.senSlopePerSec() * 3600.0;
		double slopePctPerHour = (slopeBytesPerHour / baseline) * 100.0;
		double deltaAbs = analyzed.get(analyzed.size() - 1).value() - analyzed.get(0).value();
		double deltaPct = (deltaAbs / baseline) * 100.0;

		if (isLateOnsetOnly(analyzed)) {
			return evidence(
					SeriesVerdict.of(PluginRunStatus.WARN,
							String.format(Locale.ROOT,
									"Подозрение на рост только в хвосте (~последние %.1fч), недостаточно горизонта (slope=%.3f%%/ч, Δ=%.1f МиБ)",
									lateOnsetTailHours, slopePctPerHour, deltaAbs / (1024 * 1024))),
					analyzed, fit, slopeBytesPerHour, slopePctPerHour, deltaAbs, deltaPct);
		}

		if (!fit.significant() || slopeBytesPerHour <= 0) {
			return evidence(
					SeriesVerdict.of(PluginRunStatus.OK,
							String.format(Locale.ROOT,
									"Значимого роста нет (p=%.3f, slope=%.2f МиБ/ч, %.3f%%/ч)",
									fit.pValueTwoTailed(), slopeBytesPerHour / (1024 * 1024), slopePctPerHour)),
					analyzed, fit, slopeBytesPerHour, slopePctPerHour, deltaAbs, deltaPct);
		}

		if (isStepChangeOnly(analyzed)) {
			return evidence(
					SeriesVerdict.of(PluginRunStatus.WARN,
							String.format(Locale.ROOT,
									"Похоже на step-change, а не утечку (Δ=%.1f МиБ, slope=%.3f%%/ч)",
									deltaAbs / (1024 * 1024), slopePctPerHour)),
					analyzed, fit, slopeBytesPerHour, slopePctPerHour, deltaAbs, deltaPct);
		}

		if (deltaAbs < minAbsGrowthBytes) {
			return evidence(
					SeriesVerdict.of(PluginRunStatus.OK,
							String.format(Locale.ROOT,
									"Прирост слишком мал для FAIL (Δ=%.1f МиБ < min %.0f МиБ)",
									deltaAbs / (1024 * 1024), minAbsGrowthBytes / (1024.0 * 1024.0))),
					analyzed, fit, slopeBytesPerHour, slopePctPerHour, deltaAbs, deltaPct);
		}

		if (slopePctPerHour >= slopeFailPctPerHour && deltaPct >= slopeFailPctPerHour * durationHours(analyzed) * 0.5) {
			return evidence(
					SeriesVerdict.of(PluginRunStatus.FAIL,
							String.format(Locale.ROOT,
									"Утечка: устойчивый рост %.3f%%/ч (%.1f МиБ/ч), Δ=%.1f МиБ (%.2f%%), p=%.3f",
									slopePctPerHour, slopeBytesPerHour / (1024 * 1024),
									deltaAbs / (1024 * 1024), deltaPct, fit.pValueTwoTailed())),
					analyzed, fit, slopeBytesPerHour, slopePctPerHour, deltaAbs, deltaPct);
		}

		if (slopePctPerHour >= slopeWarnPctPerHour) {
			return evidence(
					SeriesVerdict.of(PluginRunStatus.WARN,
							String.format(Locale.ROOT,
									"Подозрение на рост: %.3f%%/ч (%.1f МиБ/ч), Δ=%.1f МиБ, p=%.3f (ниже порога FAIL %.2f%%/ч)",
									slopePctPerHour, slopeBytesPerHour / (1024 * 1024),
									deltaAbs / (1024 * 1024), fit.pValueTwoTailed(), slopeFailPctPerHour)),
					analyzed, fit, slopeBytesPerHour, slopePctPerHour, deltaAbs, deltaPct);
		}

		return evidence(
				SeriesVerdict.of(PluginRunStatus.OK,
						String.format(Locale.ROOT,
								"Рост незначительный: %.3f%%/ч, Δ=%.1f МиБ",
								slopePctPerHour, deltaAbs / (1024 * 1024))),
				analyzed, fit, slopeBytesPerHour, slopePctPerHour, deltaAbs, deltaPct);
	}

	private SeriesVerdict evidence(
			SeriesVerdict base,
			List<MetricPoint> analyzed,
			TrendStatistics.FitResult fit,
			double slopeBytesPerHour,
			double slopePctPerHour,
			double deltaAbs,
			double deltaPct) {
		Double p = fit == null ? null : fit.pValueTwoTailed();
		Double sen = fit == null ? null : fit.senSlopePerSec();
		if (analyzed == null || analyzed.size() < 2) {
			return base.withEvidence(null, null, null, null, p, sen);
		}
		return base.withEvidence(slopeBytesPerHour, slopePctPerHour, deltaAbs, deltaPct, p, sen);
	}

	/**
	 * Step-change heuristic: first half has most of the jump; second-half Sen slope ≈ flat
	 * while overall delta is large.
	 */
	boolean isStepChangeOnly(List<MetricPoint> series) {
		if (series.size() < minPoints) {
			return false;
		}
		int mid = series.size() / 2;
		List<MetricPoint> first = series.subList(0, mid);
		List<MetricPoint> second = series.subList(mid, series.size());
		double d1 = first.get(first.size() - 1).value() - first.get(0).value();
		double d2 = second.get(second.size() - 1).value() - second.get(0).value();
		double total = series.get(series.size() - 1).value() - series.get(0).value();
		if (total <= 0) {
			return false;
		}
		TrendStatistics.FitResult secondFit = TrendStatistics.fit(second, significanceAlpha);
		double secondSlopePerHour = secondFit.senSlopePerSec() * 3600.0;
		double baseline = Math.max(series.get(0).value(), 1.0);
		double secondPctPerHour = (secondSlopePerHour / baseline) * 100.0;
		// Most growth in first half, second half nearly flat
		return d1 > 0.7 * total && d2 < 0.25 * total && secondPctPerHour < slopeWarnPctPerHour;
	}

	/**
	 * Late-onset: head of series flat/insignificant; significant growth concentrated in the tail.
	 */
	boolean isLateOnsetOnly(List<MetricPoint> series) {
		if (series.size() < minPoints || durationHours(series) < minAnalyzeHours) {
			return false;
		}
		long tEnd = series.get(series.size() - 1).timestampSec();
		long tailStart = tEnd - Math.round(lateOnsetTailHours * 3600.0);
		List<MetricPoint> head = dropAfterExclusive(series, tailStart);
		List<MetricPoint> tail = dropBefore(series, tailStart);
		if (head.size() < minPoints / 2 || tail.size() < 4) {
			return false;
		}
		TrendStatistics.FitResult headFit = TrendStatistics.fit(head, significanceAlpha);
		TrendStatistics.FitResult tailFit = TrendStatistics.fit(tail, significanceAlpha);
		double baseline = Math.max(series.get(0).value(), 1.0);
		double headPct = (headFit.senSlopePerSec() * 3600.0 / baseline) * 100.0;
		double tailPct = (tailFit.senSlopePerSec() * 3600.0 / baseline) * 100.0;
		boolean headFlat = !headFit.significant() || headPct < slopeWarnPctPerHour;
		boolean tailGrowing = tailFit.significant() && tailPct >= slopeWarnPctPerHour && tailFit.senSlopePerSec() > 0;
		return headFlat && tailGrowing && durationHours(tail) <= lateOnsetTailHours + 0.25;
	}

	private static List<MetricPoint> dropBefore(List<MetricPoint> series, long tsInclusive) {
		List<MetricPoint> out = new ArrayList<>();
		for (MetricPoint p : series) {
			if (p.timestampSec() >= tsInclusive) {
				out.add(p);
			}
		}
		return out;
	}

	private static List<MetricPoint> dropAfterExclusive(List<MetricPoint> series, long tsExclusiveEnd) {
		List<MetricPoint> out = new ArrayList<>();
		for (MetricPoint p : series) {
			if (p.timestampSec() < tsExclusiveEnd) {
				out.add(p);
			}
		}
		return out;
	}

	private static double durationHours(List<MetricPoint> series) {
		if (series.size() < 2) {
			return 0;
		}
		return (series.get(series.size() - 1).timestampSec() - series.get(0).timestampSec()) / 3600.0;
	}

	private static double medianFirst(List<MetricPoint> series, int count) {
		List<Double> values = new ArrayList<>();
		for (int i = 0; i < count && i < series.size(); i++) {
			values.add(series.get(i).value());
		}
		values.sort(Double::compareTo);
		int m = values.size();
		if (m == 0) {
			return 0;
		}
		if (m % 2 == 1) {
			return values.get(m / 2);
		}
		return 0.5 * (values.get(m / 2 - 1) + values.get(m / 2));
	}
}
