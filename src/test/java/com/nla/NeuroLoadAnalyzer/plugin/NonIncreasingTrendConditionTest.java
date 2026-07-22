package com.nla.NeuroLoadAnalyzer.plugin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonIncreasingTrendConditionTest {

	private final NonIncreasingTrendCondition condition = NonIncreasingTrendCondition.defaults();

	@Test
	void flatSeriesIsOk() {
		List<MetricPoint> series = linear(12, 300, 2.0, 0);
		SeriesVerdict verdict = condition.evaluate(series);
		assertEquals(PluginRunStatus.OK, verdict.status(), verdict.reason());
	}

	@Test
	void decreasingSeriesIsOk() {
		List<MetricPoint> series = linear(12, 300, 10.0, -0.5);
		SeriesVerdict verdict = condition.evaluate(series);
		assertEquals(PluginRunStatus.OK, verdict.status(), verdict.reason());
		assertTrue(verdict.reason().toLowerCase().contains("убывающ")
				|| verdict.reason().toLowerCase().contains("стабильн"), verdict.reason());
	}

	@Test
	void growingSeriesIsFail() {
		List<MetricPoint> series = linear(20, 300, 1.0, 0.4);
		SeriesVerdict verdict = condition.evaluate(series);
		assertEquals(PluginRunStatus.FAIL, verdict.status(), verdict.reason());
		assertTrue(verdict.slopePctPerHour() != null && verdict.slopePctPerHour() > 0, verdict.reason());
	}

	@Test
	void shortSeriesIsWarn() {
		List<MetricPoint> series = linear(3, 300, 1.0, 1.0);
		SeriesVerdict verdict = condition.evaluate(series);
		assertEquals(PluginRunStatus.WARN, verdict.status(), verdict.reason());
	}

	private static List<MetricPoint> linear(int points, int stepSec, double start, double perStep) {
		List<MetricPoint> series = new ArrayList<>();
		long t0 = 1_700_000_000L;
		for (int i = 0; i < points; i++) {
			series.add(new MetricPoint(t0 + (long) i * stepSec, start + perStep * i));
		}
		return series;
	}
}
