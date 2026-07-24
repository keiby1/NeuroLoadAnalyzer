package com.nla.NeuroLoadAnalyzer.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BandedThresholdConditionTest {

	@Test
	void warnThenFailBands() {
		BandedThresholdCondition c = BandedThresholdCondition.warnThenFail(78, 80);
		assertEquals(PluginRunStatus.OK, c.evaluate(77.9).status());
		assertEquals(PluginRunStatus.WARN, c.evaluate(78).status());
		assertEquals(PluginRunStatus.WARN, c.evaluate(79.9).status());
		assertEquals(PluginRunStatus.FAIL, c.evaluate(80).status());
		assertEquals(PluginRunStatus.FAIL, c.evaluate(85).status());
	}

	@Test
	void infoThenFailThrottleBands() {
		BandedThresholdCondition c = BandedThresholdCondition.infoThenFail(1, 10);
		assertEquals(PluginRunStatus.OK, c.evaluate(1).status());
		assertEquals(PluginRunStatus.INFO, c.evaluate(2).status());
		assertEquals(PluginRunStatus.INFO, c.evaluate(10).status());
		assertEquals(PluginRunStatus.FAIL, c.evaluate(10.1).status());
		assertEquals(PluginRunStatus.FAIL, c.evaluate(15).status());
	}

	@Test
	void infoThenFailInclusiveTcpBands() {
		BandedThresholdCondition c = BandedThresholdCondition.infoThenFailInclusive(12_000, 16_000);
		assertEquals(PluginRunStatus.OK, c.evaluate(12_000).status());
		assertEquals(PluginRunStatus.INFO, c.evaluate(13_000).status());
		assertEquals(PluginRunStatus.INFO, c.evaluate(15_999).status());
		assertEquals(PluginRunStatus.FAIL, c.evaluate(16_000).status());
		assertEquals(PluginRunStatus.FAIL, c.evaluate(20_000).status());
	}
}
