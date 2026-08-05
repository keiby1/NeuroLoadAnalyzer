package com.nla.NeuroLoadAnalyzer.report;

import com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.TypeReportGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerdictMapperTest {

	@Test
	void emptyTypeGroupsYieldInsufficientData() {
		assertEquals(AnalysisVerdict.INSUFFICIENT_DATA, VerdictMapper.fromTypeGroups(List.of()));
		assertEquals(AnalysisVerdict.INSUFFICIENT_DATA, VerdictMapper.fromTypeGroups(null));
	}

	@Test
	void singleOkIsSuccess() {
		assertEquals(AnalysisVerdict.SUCCESS, VerdictMapper.fromTypeGroups(List.of(
				group("VM", PluginRunStatus.OK))));
	}

	@Test
	void bothOkIsSuccess() {
		assertEquals(AnalysisVerdict.SUCCESS, VerdictMapper.fromTypeGroups(List.of(
				group("VM", PluginRunStatus.OK),
				group("K8S", PluginRunStatus.OK))));
	}

	@Test
	void okAndInfoIsSuccess() {
		assertEquals(AnalysisVerdict.SUCCESS, VerdictMapper.fromTypeGroups(List.of(
				group("VM", PluginRunStatus.OK),
				group("K8S", PluginRunStatus.INFO))));
	}

	@Test
	void infoOnlyIsWithRemarks() {
		assertEquals(AnalysisVerdict.WITH_REMARKS, VerdictMapper.fromTypeGroups(List.of(
				group("VM", PluginRunStatus.INFO),
				group("K8S", PluginRunStatus.INFO))));
	}

	@Test
	void warnAndOkIsWithRemarks() {
		assertEquals(AnalysisVerdict.WITH_REMARKS, VerdictMapper.fromTypeGroups(List.of(
				group("VM", PluginRunStatus.WARN),
				group("K8S", PluginRunStatus.OK))));
	}

	@Test
	void noDataAndOkIsInsufficientData() {
		assertEquals(AnalysisVerdict.INSUFFICIENT_DATA, VerdictMapper.fromTypeGroups(List.of(
				group("VM", PluginRunStatus.NO_DATA),
				group("K8S", PluginRunStatus.OK))));
	}

	@Test
	void skipAndOkIsSuccess() {
		assertEquals(AnalysisVerdict.SUCCESS, VerdictMapper.fromTypeGroups(List.of(
				group("VM", PluginRunStatus.SKIP),
				group("K8S", PluginRunStatus.OK))));
	}

	@Test
	void onlySkipIsInsufficientData() {
		assertEquals(AnalysisVerdict.INSUFFICIENT_DATA, VerdictMapper.fromTypeGroups(List.of(
				group("VM", PluginRunStatus.SKIP))));
	}

	@Test
	void failAndOkIsFailure() {
		assertEquals(AnalysisVerdict.FAILURE, VerdictMapper.fromTypeGroups(List.of(
				group("VM", PluginRunStatus.FAIL),
				group("K8S", PluginRunStatus.OK))));
	}

	@Test
	void failBeatsWarn() {
		assertEquals(AnalysisVerdict.FAILURE, VerdictMapper.fromTypeGroups(List.of(
				group("VM", PluginRunStatus.FAIL),
				group("K8S", PluginRunStatus.WARN))));
	}

	@Test
	void fromWorstStatusMapping() {
		assertEquals(AnalysisVerdict.FAILURE, VerdictMapper.fromWorstStatus(PluginRunStatus.FAIL));
		assertEquals(AnalysisVerdict.WITH_REMARKS, VerdictMapper.fromWorstStatus(PluginRunStatus.WARN));
		assertEquals(AnalysisVerdict.WITH_REMARKS, VerdictMapper.fromWorstStatus(PluginRunStatus.INFO));
		assertEquals(AnalysisVerdict.INSUFFICIENT_DATA, VerdictMapper.fromWorstStatus(PluginRunStatus.NO_DATA));
		assertEquals(AnalysisVerdict.INSUFFICIENT_DATA, VerdictMapper.fromWorstStatus(PluginRunStatus.SKIP));
		assertEquals(AnalysisVerdict.SUCCESS, VerdictMapper.fromWorstStatus(PluginRunStatus.OK));
		assertEquals(AnalysisVerdict.INSUFFICIENT_DATA, VerdictMapper.fromWorstStatus(null));
	}

	@Test
	void labelsAndCss() {
		assertEquals("Успешно", AnalysisVerdict.SUCCESS.labelRu());
		assertEquals("С замечаниями", AnalysisVerdict.WITH_REMARKS.labelRu());
		assertEquals("Недостаточно данных", AnalysisVerdict.INSUFFICIENT_DATA.labelRu());
		assertEquals("Неуспешно", AnalysisVerdict.FAILURE.labelRu());
		assertEquals("green", AnalysisVerdict.SUCCESS.cssClass());
		assertEquals("orange", AnalysisVerdict.WITH_REMARKS.cssClass());
		assertEquals("yellow", AnalysisVerdict.INSUFFICIENT_DATA.cssClass());
		assertEquals("red", AnalysisVerdict.FAILURE.cssClass());
	}

	private static TypeReportGroup group(String prefix, PluginRunStatus status) {
		return new TypeReportGroup(prefix, prefix, status, List.of());
	}
}
