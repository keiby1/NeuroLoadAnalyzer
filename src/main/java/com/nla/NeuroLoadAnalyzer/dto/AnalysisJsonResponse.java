package com.nla.NeuroLoadAnalyzer.dto;

import java.util.List;

/**
 * JSON analysis response: overall verdict + nested report cards.
 */
public record AnalysisJsonResponse(
		String status,
		List<ReportCardNode> details
) {
}
