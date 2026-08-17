package com.nla.NeuroLoadAnalyzer.plugin;

/**
 * Optional metric source for inventory-based checks (K8S workloads).
 * {@link #NONE} — PromQL via VictoriaMetrics as usual (VM plugins).
 */
public enum WorkloadMetric {
	NONE,
	/** Peak CPU %% via rate[cpu-rate-window] (default 5m), then max_over_time. */
	K8S_CPU_MAX_PERCENT,
	/** Peak CPU %% via rate[1m], then max_over_time — catches ~1m spikes better. */
	K8S_CPU_MAX_PERCENT_1M,
	K8S_MEM_MAX_PERCENT,
	K8S_RESTART_INCREASE,
	K8S_THROTTLING_MAX_PERCENT,
	/** RANGE: Sen/MK on throttling % series — fail if growing. */
	K8S_THROTTLING_TREND,
	/** RANGE: Sen/MK leak detection on sum(working_set) per workload (same as VM RAM growth / leak). */
	K8S_MEM_LEAK_TREND
}
