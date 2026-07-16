package com.nla.NeuroLoadAnalyzer.report;

import com.nla.NeuroLoadAnalyzer.plugin.PluginResult;
import com.nla.NeuroLoadAnalyzer.plugin.PluginRunStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hierarchical report tree:
 * type prefix → software → purpose → parameter value (leaf checks).
 */
public final class ReportTreeBuilder {

	private ReportTreeBuilder() {
	}

	public static List<TypeReportGroup> build(List<PluginResult> results) {
		if (results == null || results.isEmpty()) {
			return List.of();
		}

		Map<String, Map<String, Map<String, Map<String, List<PluginResult>>>>> tree = new LinkedHashMap<>();

		for (PluginResult result : results) {
			String type = nullToUnknown(result.targetType());
			String software = nullToUnknown(result.software());
			String purpose = nullToUnknown(result.purpose());
			String valueKey = result.parameterName() + "=" + result.parameterValue();

			tree
					.computeIfAbsent(type, k -> new LinkedHashMap<>())
					.computeIfAbsent(software, k -> new LinkedHashMap<>())
					.computeIfAbsent(purpose, k -> new LinkedHashMap<>())
					.computeIfAbsent(valueKey, k -> new ArrayList<>())
					.add(result);
		}

		List<TypeReportGroup> groups = new ArrayList<>();
		for (Map.Entry<String, Map<String, Map<String, Map<String, List<PluginResult>>>>> typeEntry : tree.entrySet()) {
			List<SoftwareReportNode> softwares = new ArrayList<>();
			for (Map.Entry<String, Map<String, Map<String, List<PluginResult>>>> softEntry : typeEntry.getValue().entrySet()) {
				List<PurposeReportNode> purposes = new ArrayList<>();
				for (Map.Entry<String, Map<String, List<PluginResult>>> purposeEntry : softEntry.getValue().entrySet()) {
					List<ValueReportNode> values = new ArrayList<>();
					for (Map.Entry<String, List<PluginResult>> valueEntry : purposeEntry.getValue().entrySet()) {
						List<PluginResult> leafResults = List.copyOf(valueEntry.getValue());
						PluginResult sample = leafResults.get(0);
						PluginRunStatus valueStatus = StatusAggregator.aggregate(statusesOf(leafResults));
						values.add(new ValueReportNode(
								sample.parameterName(),
								sample.parameterValue(),
								valueStatus,
								leafResults));
					}
					PluginRunStatus purposeStatus = StatusAggregator.aggregate(values.stream().map(ValueReportNode::status).toList());
					purposes.add(new PurposeReportNode(purposeEntry.getKey(), purposeStatus, List.copyOf(values)));
				}
				PluginRunStatus softStatus = StatusAggregator.aggregate(purposes.stream().map(PurposeReportNode::status).toList());
				softwares.add(new SoftwareReportNode(softEntry.getKey(), softStatus, List.copyOf(purposes)));
			}
			PluginRunStatus typeStatus = StatusAggregator.aggregate(softwares.stream().map(SoftwareReportNode::status).toList());
			String typePrefix = typeEntry.getKey();
			groups.add(new TypeReportGroup(
					typePrefix,
					TargetTypeLabels.displayName(typePrefix),
					typeStatus,
					List.copyOf(softwares)));
		}
		return List.copyOf(groups);
	}

	private static List<PluginRunStatus> statusesOf(Collection<PluginResult> results) {
		return results.stream().map(PluginResult::status).toList();
	}

	private static String nullToUnknown(String value) {
		return value == null || value.isBlank() ? "unknown" : value;
	}

	public record TypeReportGroup(
			String typePrefix,
			String displayName,
			PluginRunStatus status,
			List<SoftwareReportNode> softwares
	) {
	}

	public record SoftwareReportNode(
			String software,
			PluginRunStatus status,
			List<PurposeReportNode> purposes
	) {
	}

	public record PurposeReportNode(
			String purpose,
			PluginRunStatus status,
			List<ValueReportNode> values
	) {
	}

	public record ValueReportNode(
			String parameterName,
			String parameterValue,
			PluginRunStatus status,
			List<PluginResult> results
	) {
	}
}
