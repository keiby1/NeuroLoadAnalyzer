package com.nla.NeuroLoadAnalyzer.dto;

/**
 * Query parameter whose name matches {@code Type_Software_Purpose} (e.g. {@code VM_Kafka_GW}).
 */
public record TypedTarget(
		String rawName,
		String type,
		String software,
		String purpose,
		String value
) {
	public String canonicalName() {
		return type + "_" + software + "_" + purpose;
	}
}
