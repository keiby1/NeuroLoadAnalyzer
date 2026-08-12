package com.nla.NeuroLoadAnalyzer.report;

import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPlugin;
import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus;
import com.nla.NeuroLoadAnalyzer.plugin.ThresholdCondition;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.PurposeReportNode;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.TypeReportGroup;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.ValueReportNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportTreeBuilderTest {

	private final AnalysisPlugin plugin = new AnalysisPlugin(
			"CPU > 80%", "VM", "up{instance=~\"$VM\"}", ThresholdCondition.greaterThan(80));

	@Test
	void buildsHierarchyAndAggregatesStatus() {
		TypedTarget kafkaGw1 = new TypedTarget("VM_Kafka_GW", "VM", "Kafka", "GW", "server1", false);
		TypedTarget kafkaGw2 = new TypedTarget("VM_Kafka_GW", "VM", "Kafka", "GW", "server2", false);
		TypedTarget pgGw = new TypedTarget("VM_Postgre_ASD", "VM", "Postgre", "ASD", "server3", false);

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

	@Test
	void optionalFailDoesNotRaisePurposeOrType() {
		TypedTarget required = new TypedTarget("VM_Kafka_GW", "VM", "Kafka", "GW", "h1", false);
		TypedTarget optional = new TypedTarget("VM_Kafka_GW_opt", "VM", "Kafka", "GW", "h2", true);

		List<TypeReportGroup> groups = ReportTreeBuilder.build(List.of(
				PluginResult.evaluated(plugin, required, "q1", 10, false),
				PluginResult.evaluated(plugin, optional, "q2", 90, true)));

		TypeReportGroup vm = groups.get(0);
		assertEquals(PluginRunStatus.OK, vm.status());
		PurposeReportNode purpose = vm.softwares().get(0).purposes().get(0);
		assertEquals(PluginRunStatus.OK, purpose.status());
		assertEquals(2, purpose.values().size());

		ValueReportNode optValue = purpose.values().stream()
				.filter(ValueReportNode::optional)
				.findFirst()
				.orElseThrow();
		assertEquals("h2", optValue.parameterValue());
		assertEquals(PluginRunStatus.FAIL, optValue.status());
		assertTrue(optValue.optional());

		ValueReportNode reqValue = purpose.values().stream()
				.filter(v -> !v.optional())
				.findFirst()
				.orElseThrow();
		assertEquals(PluginRunStatus.OK, reqValue.status());
		assertFalse(reqValue.optional());
	}

	@Test
	void onlyOptionalFailYieldsTypeOk() {
		TypedTarget optional = new TypedTarget("VM_Kafka_GW_opt", "VM", "Kafka", "GW", "h2", true);

		List<TypeReportGroup> groups = ReportTreeBuilder.build(List.of(
				PluginResult.evaluated(plugin, optional, "q2", 90, true)));

		assertEquals(PluginRunStatus.OK, groups.get(0).status());
		ValueReportNode value = groups.get(0).softwares().get(0).purposes().get(0).values().get(0);
		assertTrue(value.optional());
		assertEquals(PluginRunStatus.FAIL, value.status());
	}

	@Test
	void optionalFailDoesNotMakeVerdictFailure() {
		TypedTarget optional = new TypedTarget("VM_Kafka_GW_opt", "VM", "Kafka", "GW", "h2", true);
		List<TypeReportGroup> groups = ReportTreeBuilder.build(List.of(
				PluginResult.evaluated(plugin, optional, "q2", 90, true)));
		assertEquals(AnalysisVerdict.SUCCESS, VerdictMapper.fromTypeGroups(groups));
	}
}
