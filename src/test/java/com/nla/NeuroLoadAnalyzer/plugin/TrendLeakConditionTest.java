package com.nla.NeuroLoadAnalyzer.plugin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendLeakConditionTest {

	private final TrendLeakCondition condition = TrendLeakCondition.defaults();

	@Test
	void flatSeriesIsOk() {
		List<MetricPoint> series = linearSeries(12, 3600, 8L * 1024 * 1024 * 1024, 0);
		SeriesVerdict verdict = condition.evaluate(series);
		assertEquals(PluginRunStatus.OK, verdict.status(), verdict.reason());
	}

	@Test
	void strongLinearGrowthIsFail() {
		// ~0.3%/hour of 10 GiB baseline after warmup
		double baseline = 10L * 1024 * 1024 * 1024;
		double perHour = baseline * 0.003;
		List<MetricPoint> series = linearSeries(14, 3600, baseline, perHour);
		SeriesVerdict verdict = condition.evaluate(series);
		assertEquals(PluginRunStatus.FAIL, verdict.status(), verdict.reason());
		assertTrue(verdict.slopePctPerHour() != null && verdict.slopePctPerHour() >= 0.2, verdict.reason());
	}

	@Test
	void shortWindowIsWarn() {
		double baseline = 8L * 1024 * 1024 * 1024;
		List<MetricPoint> series = linearSeries(3, 3600, baseline, baseline * 0.01);
		SeriesVerdict verdict = condition.evaluate(series);
		assertEquals(PluginRunStatus.WARN, verdict.status(), verdict.reason());
		assertTrue(verdict.reason().toLowerCase().contains("мало")
				|| verdict.reason().toLowerCase().contains("данных"), verdict.reason());
	}

	@Test
	void lateOnsetIsWarnNotFail() {
		double baseline = 10L * 1024 * 1024 * 1024;
		List<MetricPoint> series = new ArrayList<>();
		long t0 = 1_700_000_000L;
		int step = 900; // 15 min → enough points in a 2h tail
		// 10 hours flat
		for (int i = 0; i <= 10 * 4; i++) {
			series.add(new MetricPoint(t0 + (long) i * step, baseline));
		}
		// last ~2 hours strong growth
		long growthStart = t0 + 10L * 3600;
		double perHour = baseline * 0.015;
		for (int i = 1; i <= 8; i++) {
			long ts = growthStart + (long) i * step;
			double hours = (i * step) / 3600.0;
			series.add(new MetricPoint(ts, baseline + perHour * hours));
		}
		SeriesVerdict verdict = condition.evaluate(series);
		assertEquals(PluginRunStatus.WARN, verdict.status(), verdict.reason());
	}

	@Test
	void stepChangeIsNotFail() {
		double baseline = 10L * 1024 * 1024 * 1024;
		long jump = 500L * 1024 * 1024;
		List<MetricPoint> series = new ArrayList<>();
		long t0 = 1_700_000_000L;
		for (int i = 0; i < 6; i++) {
			series.add(new MetricPoint(t0 + i * 3600L, baseline));
		}
		for (int i = 6; i <= 14; i++) {
			series.add(new MetricPoint(t0 + i * 3600L, baseline + jump));
		}
		SeriesVerdict verdict = condition.evaluate(series);
		assertTrue(verdict.status() == PluginRunStatus.OK || verdict.status() == PluginRunStatus.WARN,
				"step-change must not be FAIL, was " + verdict.status() + ": " + verdict.reason());
	}

	/** points at each hour: value = baseline + hourIndex * perHour (hourIndex from 0). */
	private static List<MetricPoint> linearSeries(int hoursInclusive, int stepSec, double baseline, double perHour) {
		List<MetricPoint> points = new ArrayList<>();
		long t0 = 1_700_000_000L;
		int steps = hoursInclusive * (3600 / stepSec);
		for (int i = 0; i <= steps; i++) {
			double hours = (i * (double) stepSec) / 3600.0;
			points.add(new MetricPoint(t0 + (long) i * stepSec, baseline + perHour * hours));
		}
		return points;
	}
}
