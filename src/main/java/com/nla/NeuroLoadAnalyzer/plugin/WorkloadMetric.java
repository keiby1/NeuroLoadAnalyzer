package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * Optional metric source for inventory-based checks (K8S workloads).
 * {@link #NONE} — PromQL via VictoriaMetrics as usual (VM plugins).
 */
public enum WorkloadMetric {
	NONE,
	K8S_CPU_MAX_PERCENT,
	K8S_MEM_MAX_PERCENT,
	K8S_RESTART_INCREASE,
	K8S_THROTTLING_MAX_PERCENT,
	/** RANGE: Sen/MK on throttling % series — fail if growing. */
	K8S_THROTTLING_TREND
}
