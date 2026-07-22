package com.nla.NeuroLoadAnalyzer.plugin;

import java.util.List;
import java.util.Locale;

/**
 * Fails when the series has a statistically significant upward Sen/Mann–Kendall trend.
 * Flat or decreasing trends are OK. Designed for K8S CPU throttling % over {@code from}–{@code to}.
 */
public final class NonIncreasingTrendCondition implements SeriesAnalysisCondition {

	public static final int DEFAULT_MIN_POINTS = 6;
	public static final double DEFAULT_SIGNIFICANCE_ALPHA = 0.05;
	/** Ignore tiny positive slopes (percentage points per hour). */
	public static final double DEFAULT_MIN_SLOPE_PP_PER_HOUR = 0.01;

	private final int minPoints;
	private final double significanceAlpha;
	private final double minSlopePpPerHour;

	public NonIncreasingTrendCondition() {
		this(DEFAULT_MIN_POINTS, DEFAULT_SIGNIFICANCE_ALPHA, DEFAULT_MIN_SLOPE_PP_PER_HOUR);
	}

	public NonIncreasingTrendCondition(int minPoints, double significanceAlpha, double minSlopePpPerHour) {
		this.minPoints = Math.max(3, minPoints);
		this.significanceAlpha = significanceAlpha;
		this.minSlopePpPerHour = Math.max(0, minSlopePpPerHour);
	}

	public static NonIncreasingTrendCondition defaults() {
		return new NonIncreasingTrendCondition();
	}

	@Override
	public String description() {
		return "тренд не растущий (Sen+MK): OK если стабильный/убывающий, FAIL если значимый рост";
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
		if (sorted.size() < minPoints) {
			return SeriesVerdict.of(PluginRunStatus.WARN,
					String.format(Locale.ROOT,
							"Мало точек для анализа тренда (точек=%d, нужно ≥%d)",
							sorted.size(), minPoints));
		}

		TrendStatistics.FitResult fit = TrendStatistics.fit(sorted, significanceAlpha);
		double slopePpPerHour = fit.senSlopePerSec() * 3600.0;
		double deltaPp = sorted.get(sorted.size() - 1).value() - sorted.get(0).value();

		SeriesVerdict base;
		if (fit.significant() && slopePpPerHour > minSlopePpPerHour) {
			base = SeriesVerdict.of(PluginRunStatus.FAIL,
					String.format(Locale.ROOT,
							"Растущий тренд троттлинга: %.3f п.п./ч, Δ=%.2f п.п., p=%.3f",
							slopePpPerHour, deltaPp, fit.pValueTwoTailed()));
		} else if (slopePpPerHour < -minSlopePpPerHour) {
			base = SeriesVerdict.of(PluginRunStatus.OK,
					String.format(Locale.ROOT,
							"Убывающий тренд: %.3f п.п./ч, Δ=%.2f п.п., p=%.3f",
							slopePpPerHour, deltaPp, fit.pValueTwoTailed()));
		} else {
			base = SeriesVerdict.of(PluginRunStatus.OK,
					String.format(Locale.ROOT,
							"Стабильный тренд (значимого роста нет): %.3f п.п./ч, Δ=%.2f п.п., p=%.3f",
							slopePpPerHour, deltaPp, fit.pValueTwoTailed()));
		}
		return base.withEvidence(
				null,
				slopePpPerHour,
				null,
				deltaPp,
				fit.pValueTwoTailed(),
				fit.senSlopePerSec());
	}
}
