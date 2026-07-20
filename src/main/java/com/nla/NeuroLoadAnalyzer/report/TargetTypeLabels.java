package com.nla.NeuroLoadAnalyzer.report;

/**
 * Maps type prefixes ({@code VM}, …) to human-readable report group titles.
 */
public final class TargetTypeLabels {

	private TargetTypeLabels() {
	}

	public static String displayName(String typePrefix) {
		if (typePrefix == null || typePrefix.isBlank()) {
			return "Прочее";
		}
		return switch (typePrefix.trim().toUpperCase()) {
			case "VM" -> "Виртуальные сервера";
			case "K8S" -> "K8S";
			default -> typePrefix.trim();
		};
	}
}
