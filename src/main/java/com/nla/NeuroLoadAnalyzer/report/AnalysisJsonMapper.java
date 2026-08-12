package com.nla.NeuroLoadAnalyzer.report;

import com.nla.NeuroLoadAnalyzer.dto.AnalysisJsonResponse;
import com.nla.NeuroLoadAnalyzer.dto.AnalysisReport;
import com.nla.NeuroLoadAnalyzer.dto.ReportCardNode;
import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.PurposeReportNode;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.SoftwareReportNode;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.TypeReportGroup;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.ValueReportNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds JSON API payload from {@link AnalysisReport} tree (same hierarchy as HTML cards).
 */
public final class AnalysisJsonMapper {

	private AnalysisJsonMapper() {
	}

	public static AnalysisJsonResponse fromReport(AnalysisReport report) {
		AnalysisVerdict verdict = report != null && report.verdict() != null
				? report.verdict()
				: AnalysisVerdict.INSUFFICIENT_DATA;
		List<TypeReportGroup> groups = report != null && report.typeGroups() != null
				? report.typeGroups()
				: List.of();
		List<ReportCardNode> details = new ArrayList<>(groups.size());
		for (TypeReportGroup typeGroup : groups) {
			details.add(toTypeCard(typeGroup));
		}
		return new AnalysisJsonResponse(verdict.labelRu(), List.copyOf(details));
	}

	private static ReportCardNode toTypeCard(TypeReportGroup typeGroup) {
		List<ReportCardNode> children = new ArrayList<>();
		for (SoftwareReportNode software : typeGroup.softwares()) {
			children.add(toSoftwareCard(software));
		}
		return ReportCardNode.of(typeGroup.displayName(), statusLabel(typeGroup.status()), children);
	}

	private static ReportCardNode toSoftwareCard(SoftwareReportNode software) {
		List<ReportCardNode> children = new ArrayList<>();
		for (PurposeReportNode purpose : software.purposes()) {
			children.add(toPurposeCard(purpose));
		}
		return ReportCardNode.of(software.software(), statusLabel(software.status()), children);
	}

	private static ReportCardNode toPurposeCard(PurposeReportNode purpose) {
		boolean flattenValues = purpose.values().stream().allMatch(AnalysisJsonMapper::isBlankValueNode);
		List<ReportCardNode> children = new ArrayList<>();
		if (flattenValues) {
			for (ValueReportNode value : purpose.values()) {
				for (PluginResult result : value.results()) {
					children.add(toCheckCard(result));
				}
			}
		} else {
			for (ValueReportNode value : purpose.values()) {
				children.add(toValueCard(value));
			}
		}
		return ReportCardNode.of(purpose.purpose(), statusLabel(purpose.status()), children);
	}

	private static ReportCardNode toValueCard(ValueReportNode value) {
		List<ReportCardNode> children = new ArrayList<>();
		for (PluginResult result : value.results()) {
			children.add(toCheckCard(result));
		}
		String name = value.parameterValue() != null && !value.parameterValue().isBlank()
				? value.parameterValue()
				: value.parameterName();
		Boolean optional = value.optional() ? Boolean.TRUE : null;
		return ReportCardNode.of(name, statusLabel(value.status()), children, optional);
	}

	private static ReportCardNode toCheckCard(PluginResult result) {
		return ReportCardNode.of(result.pluginName(), statusLabel(result.status()));
	}

	private static boolean isBlankValueNode(ValueReportNode value) {
		return value.parameterValue() == null || value.parameterValue().isBlank();
	}

	private static String statusLabel(com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus status) {
		return StatusAggregator.label(status);
	}
}
