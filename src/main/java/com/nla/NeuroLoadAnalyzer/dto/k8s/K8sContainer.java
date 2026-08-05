package com.nla.NeuroLoadAnalyzer.dto.k8s;

/**
 * Container resource / usage snapshot for a K8s workload.
 */
public record K8sContainer(
		String name,
		int cpuMaxPercent,
		/** Peak %% from rate[1m] max_over_time (finer than {@link #cpuMaxPercent}). */
		int cpuMaxPercent1m,
		int memMaxPercent,
		int cpuAvgPercent,
		int memAvgPercent,
		int throttlingPercent,
		double restartIncrease
) {
}
