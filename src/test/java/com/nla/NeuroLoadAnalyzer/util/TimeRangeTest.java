package com.nla.NeuroLoadAnalyzer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeRangeTest {

	@Test
	void usesExplicitWindowFromGrafanaMillis() {
		TimeRange range = TimeRange.of(1_710_000_000_000L, 1_710_003_600_000L, "1h");

		assertEquals("3600s", range.rangeForPromQl());
		assertEquals(1_710_003_600L, range.evaluationTimeSec());
		assertTrue(range.hasExplicitWindow());
	}

	@Test
	void fallsBackToDefaultWhenWindowMissing() {
		TimeRange range = TimeRange.of(null, null, "1h");

		assertEquals("1h", range.rangeForPromQl());
		assertNull(range.evaluationTimeSec());
		assertFalse(range.hasExplicitWindow());
	}
}
