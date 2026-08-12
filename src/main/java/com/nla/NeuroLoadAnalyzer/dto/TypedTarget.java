package com.nla.NeuroLoadAnalyzer.dto;

/**
 * Query parameter whose name matches {@code Type_Software_Purpose} (e.g. {@code VM_Kafka_GW})
 * or optional {@code Type_Software_Purpose_opt}.
 */
public record TypedTarget(
		String rawName,
		String type,
		String software,
		String purpose,
		String value,
		boolean optional
) {
	public String canonicalName() {
		return type + "_" + software + "_" + purpose;
	}
}
