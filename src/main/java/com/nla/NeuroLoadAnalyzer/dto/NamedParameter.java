package com.nla.NeuroLoadAnalyzer.dto;

/**
 * Single query parameter (name may repeat for multi-value Grafana variables).
 */
public record NamedParameter(String name, String value) {
}
