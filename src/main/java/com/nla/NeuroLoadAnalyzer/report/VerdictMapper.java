package com.nla.NeuroLoadAnalyzer.report;

import com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus;
import com.nla.NeuroLoadAnalyzer.report.ReportTreeBuilder.TypeReportGroup;

import java.util.Collection;
import java.util.List;

/**
 * Maps aggregated status of top-level type groups (VM / K8S) to {@link AnalysisVerdict}.
 */
public final class VerdictMapper {

	private VerdictMapper() {
	}

	public static AnalysisVerdict fromTypeGroups(Collection<TypeReportGroup> typeGroups) {
		if (typeGroups == null || typeGroups.isEmpty()) {
			return AnalysisVerdict.INSUFFICIENT_DATA;
		}
		List<PluginRunStatus> statuses = typeGroups.stream()
				.map(TypeReportGroup::status)
				.toList();
		return fromWorstStatus(StatusAggregator.aggregate(statuses));
	}

	public static AnalysisVerdict fromWorstStatus(PluginRunStatus worst) {
		if (worst == null) {
			return AnalysisVerdict.INSUFFICIENT_DATA;
		}
		return switch (worst) {
			case FAIL -> AnalysisVerdict.FAILURE;
			case WARN, INFO -> AnalysisVerdict.WITH_REMARKS;
			case NO_DATA, SKIP -> AnalysisVerdict.INSUFFICIENT_DATA;
			case OK -> AnalysisVerdict.SUCCESS;
		};
	}
}
