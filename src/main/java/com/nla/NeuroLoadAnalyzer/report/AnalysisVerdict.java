package com.nla.NeuroLoadAnalyzer.report;

/**
 * Overall analysis verdict derived from top-level type blocks (VM / K8S).
 */
public enum AnalysisVerdict {
	/** All present top blocks are OK */
	SUCCESS,
	/** WARN present, or INFO-only tops (no FAIL) */
	WITH_REMARKS,
	/** NO_DATA / SKIP dominate, or no type blocks at all */
	INSUFFICIENT_DATA,
	/** At least one top block is FAIL */
	FAILURE;

	public String labelRu() {
		return switch (this) {
			case SUCCESS -> "Успешно";
			case WITH_REMARKS -> "С замечаниями";
			case INSUFFICIENT_DATA -> "Недостаточно данных";
			case FAILURE -> "Неуспешно";
		};
	}

	/** CSS class aligned with report palette: green / orange / yellow / red. */
	public String cssClass() {
		return switch (this) {
			case SUCCESS -> "green";
			case WITH_REMARKS -> "orange";
			case INSUFFICIENT_DATA -> "yellow";
			case FAILURE -> "red";
		};
	}
}
