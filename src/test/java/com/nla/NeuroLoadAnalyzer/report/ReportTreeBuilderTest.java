package com.nla.NeuroLoadAnalyzer.report;

import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPlugin;
import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus;
import com.nla.NeuroLoadAnalyzer.plugin.ThresholdCondition;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.TypeReportGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportTreeBuilderTest {

	@Test
	void buildsHierarchyAndAggregatesStatus() {
		AnalysisPlugin plugin = new AnalysisPlugin(
				"CPU > 80%", "VM", "up{instance=~\"$VM\"}", ThresholdCondition.greaterThan(80));

		TypedTarget kafkaGw1 = new TypedTarget("VM_Kafka_GW", "VM", "Kafka", "GW", "server1");
		TypedTarget kafkaGw2 = new TypedTarget("VM_Kafka_GW", "VM", "Kafka", "GW", "server2");
		TypedTarget pgGw = new TypedTarget("VM_Postgre_ASD", "VM", "Postgre", "ASD", "server3");

		List<PluginResult> results = List.of(
				PluginResult.evaluated(plugin, kafkaGw1, "q1", 10, false),
				PluginResult.skip(plugin, kafkaGw2, "err"),
				PluginResult.evaluated(plugin, pgGw, "q3", 90, true));

		List<TypeReportGroup> groups = ReportTreeBuilder.build(results);

		assertEquals(1, groups.size());
		assertEquals("Виртуальные сервера", groups.get(0).displayName());
		assertEquals(PluginRunStatus.FAIL, groups.get(0).status());
		assertEquals(2, groups.get(0).softwares().size());
		assertEquals("Kafka", groups.get(0).softwares().get(0).software());
		assertEquals(PluginRunStatus.OK, groups.get(0).softwares().get(0).status());
	}
}
