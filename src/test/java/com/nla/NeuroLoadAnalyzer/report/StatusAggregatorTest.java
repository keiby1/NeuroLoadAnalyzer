package com.nla.NeuroLoadAnalyzer.report;

import com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusAggregatorTest {

	@Test
	void failBeatsEverything() {
		assertEquals(PluginRunStatus.FAIL, StatusAggregator.aggregate(List.of(
				PluginRunStatus.OK, PluginRunStatus.WARN, PluginRunStatus.SKIP, PluginRunStatus.FAIL)));
	}

	@Test
	void warnBeatsOkAndNoData() {
		assertEquals(PluginRunStatus.WARN, StatusAggregator.aggregate(List.of(
				PluginRunStatus.OK, PluginRunStatus.WARN, PluginRunStatus.NO_DATA)));
	}

	@Test
	void skipDoesNotOverrideOk() {
		assertEquals(PluginRunStatus.OK, StatusAggregator.aggregate(List.of(
				PluginRunStatus.OK, PluginRunStatus.OK, PluginRunStatus.SKIP)));
	}

	@Test
	void allSkipYieldsSkip() {
		assertEquals(PluginRunStatus.SKIP, StatusAggregator.aggregate(List.of(
				PluginRunStatus.SKIP, PluginRunStatus.SKIP)));
	}

	@Test
	void noDataBeatsOk() {
		assertEquals(PluginRunStatus.NO_DATA, StatusAggregator.aggregate(List.of(
				PluginRunStatus.OK, PluginRunStatus.NO_DATA)));
	}
}
