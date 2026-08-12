package com.nla.NeuroLoadAnalyzer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Nested report card for JSON API: name + status + optional children.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReportCardNode(
		String name,
		String status,
		List<ReportCardNode> children
) {
	public static ReportCardNode of(String name, String status) {
		return new ReportCardNode(name, status, List.of());
	}

	public static ReportCardNode of(String name, String status, List<ReportCardNode> children) {
		return new ReportCardNode(name, status, children == null ? List.of() : List.copyOf(children));
	}
}
