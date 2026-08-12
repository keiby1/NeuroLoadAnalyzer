package com.nla.NeuroLoadAnalyzer.report;

import com.nla.NeuroLoadAnalyzer.dto.AnalysisJsonResponse;
import com.nla.NeuroLoadAnalyzer.dto.AnalysisReport;
import com.nla.NeuroLoadAnalyzer.dto.ReportCardNode;
import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.PurposeReportNode;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.SoftwareReportNode;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.TypeReportGroup;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.ValueReportNode;
import com.nla.NeuroLoadAnalyzer.util.TimeRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisJsonMapperTest {

	@Test
	void mapsVerdictAndNestedCards() {
		PluginResult check = leaf("CPU max [5m]", PluginRunStatus.WARN);
		ValueReportNode value = new ValueReportNode("VM_Kafka_GW", "host-a", PluginRunStatus.WARN, List.of(check));
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
		assertEquals("host-a", type.children().get(0).children().get(0).children().get(0).name());
		ReportCardNode leaf = type.children().get(0).children().get(0).children().get(0).children().get(0);
		assertEquals("CPU max [5m]", leaf.name());
		assertEquals("Warn", leaf.status());
		assertTrue(leaf.children() == null || leaf.children().isEmpty());
	}

	@Test
	void flattensBlankValueNodesLikeHtml() {
		PluginResult check = leaf("CPU usage > 80%", PluginRunStatus.OK);
		ValueReportNode blank = new ValueReportNode("k8s", "", PluginRunStatus.OK, List.of(check));
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

	private static PluginResult leaf(String name, PluginRunStatus status) {
		return new PluginResult(
				name, "VM", "Kafka", "GW", "VM_Kafka_GW", "host-a",
				status, "q", "q", 1.0, ">", "msg",
				null, null, null, null, null);
	}
}
