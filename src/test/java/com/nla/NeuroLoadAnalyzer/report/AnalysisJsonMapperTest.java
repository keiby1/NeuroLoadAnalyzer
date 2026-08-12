package com.nla.NeuroLoadAnalyzer.report;

import com.nla.NeuroLoadAnalyzer.dto.AnalysisJsonResponse;
import com.nla.NeuroLoadAnalyzer.dto.AnalysisReport;
import com.nla.NeuroLoadAnalyzer.dto.ReportCardNode;
import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPlugin;
import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus;
import com.nla.NeuroLoadAnalyzer.plugin.ThresholdCondition;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.PurposeReportNode;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.SoftwareReportNode;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.TypeReportGroup;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.ValueReportNode;
import com.nla.NeuroLoadAnalyzer.util.TimeRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisJsonMapperTest {

	@Test
	void mapsVerdictAndNestedCards() {
		PluginResult check = leaf("CPU max [5m]", PluginRunStatus.WARN, false);
		ValueReportNode value = new ValueReportNode("VM_Kafka_GW", "host-a", PluginRunStatus.WARN, List.of(check), false);
		PurposeReportNode purpose = new PurposeReportNode("GW", PluginRunStatus.WARN, List.of(value));
		SoftwareReportNode soft = new SoftwareReportNode("Kafka", PluginRunStatus.WARN, List.of(purpose));
		TypeReportGroup vm = new TypeReportGroup("VM", "Виртуальные сервера", PluginRunStatus.WARN, List.of(soft));

		AnalysisReport report = new AnalysisReport(
				TimeRange.of(1_000L, 2_000L, "1h"),
				List.of(),
				List.of(check),
				List.of(vm),
				AnalysisVerdict.WITH_REMARKS,
				"ExamplePluginCatalog");

		AnalysisJsonResponse json = AnalysisJsonMapper.fromReport(report);

		assertEquals("С замечаниями", json.status());
		assertEquals(1, json.details().size());
		ReportCardNode type = json.details().get(0);
		assertEquals("Виртуальные сервера", type.name());
		assertEquals("Warn", type.status());
		assertEquals("Kafka", type.children().get(0).name());
		assertEquals("GW", type.children().get(0).children().get(0).name());
		ReportCardNode host = type.children().get(0).children().get(0).children().get(0);
		assertEquals("host-a", host.name());
		assertNull(host.optional());
		ReportCardNode leaf = host.children().get(0);
		assertEquals("CPU max [5m]", leaf.name());
		assertEquals("Warn", leaf.status());
		assertTrue(leaf.children() == null || leaf.children().isEmpty());
	}

	@Test
	void flattensBlankValueNodesLikeHtml() {
		PluginResult check = leaf("CPU usage > 80%", PluginRunStatus.OK, false);
		ValueReportNode blank = new ValueReportNode("k8s", "", PluginRunStatus.OK, List.of(check), false);
		PurposeReportNode deployment = new PurposeReportNode("api", PluginRunStatus.OK, List.of(blank));
		SoftwareReportNode ns = new SoftwareReportNode("payments", PluginRunStatus.OK, List.of(deployment));
		TypeReportGroup k8s = new TypeReportGroup("K8S", "K8S", PluginRunStatus.OK, List.of(ns));

		AnalysisReport report = new AnalysisReport(
				TimeRange.of(1_000L, 2_000L, "1h"),
				List.of(),
				List.of(check),
				List.of(k8s),
				AnalysisVerdict.SUCCESS,
				"ExamplePluginCatalog");

		ReportCardNode purpose = AnalysisJsonMapper.fromReport(report).details().get(0)
				.children().get(0).children().get(0);
		assertEquals("api", purpose.name());
		assertEquals(1, purpose.children().size());
		assertEquals("CPU usage > 80%", purpose.children().get(0).name());
	}

	@Test
	void optionalValueMarkedAndDoesNotForceFailureVerdict() {
		AnalysisPlugin plugin = new AnalysisPlugin(
				"CPU", "VM", "up", ThresholdCondition.greaterThan(80));
		TypedTarget opt = new TypedTarget("VM_Kafka_GW_opt", "VM", "Kafka", "GW", "h2", true);
		PluginResult failOpt = PluginResult.evaluated(plugin, opt, "q", 90, true);
		List<TypeReportGroup> groups = ReportTreeBuilder.build(List.of(failOpt));

		AnalysisReport report = new AnalysisReport(
				TimeRange.of(1_000L, 2_000L, "1h"),
				List.of(),
				List.of(failOpt),
				groups,
				VerdictMapper.fromTypeGroups(groups),
				"ExamplePluginCatalog");

		AnalysisJsonResponse json = AnalysisJsonMapper.fromReport(report);
		assertEquals("Успешно", json.status());
		ReportCardNode host = json.details().get(0).children().get(0).children().get(0).children().get(0);
		assertEquals("h2", host.name());
		assertEquals(Boolean.TRUE, host.optional());
		assertEquals("Fail", host.status());
		assertEquals("OK", json.details().get(0).status());
	}

	private static PluginResult leaf(String name, PluginRunStatus status, boolean optional) {
		return new PluginResult(
				name, "VM", "Kafka", "GW", "VM_Kafka_GW", "host-a",
				status, "q", "q", 1.0, ">", "msg",
				null, null, null, null, null, optional);
	}
}
