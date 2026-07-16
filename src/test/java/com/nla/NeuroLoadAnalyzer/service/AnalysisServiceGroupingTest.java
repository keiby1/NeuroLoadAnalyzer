package com.nla.NeuroLoadAnalyzer.service;

import com.nla.NeuroLoadAnalyzer.dto.SoftwareReportGroup;
import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPlugin;
import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.plugin.ThresholdCondition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisServiceGroupingTest {

	@Test
	void groupsResultsBySoftware() {
		AnalysisPlugin plugin = new AnalysisPlugin(
				"CPU > 80%",
				"VM",
				"up{instance=~\"$VM\"}",
				ThresholdCondition.greaterThan(80));

		TypedTarget kafka1 = new TypedTarget("VM_Kafka_GW", "VM", "Kafka", "GW", "server1");
		TypedTarget kafka2 = new TypedTarget("VM_Kafka_GW", "VM", "Kafka", "GW", "server2");
		TypedTarget pg = new TypedTarget("VM_Postgre_ASD", "VM", "Postgre", "ASD", "server3");

		List<PluginResult> results = List.of(
				PluginResult.evaluated(plugin, kafka1, "q1", 10, false),
				PluginResult.evaluated(plugin, kafka2, "q2", 90, true),
				PluginResult.noData(plugin, pg, "q3"));

		List<SoftwareReportGroup> groups = AnalysisService.groupBySoftware(results);

		assertEquals(2, groups.size());
		assertEquals("Kafka", groups.get(0).software());
		assertEquals(2, groups.get(0).results().size());
		assertEquals("Postgre", groups.get(1).software());
		assertEquals(1, groups.get(1).results().size());
	}
}
