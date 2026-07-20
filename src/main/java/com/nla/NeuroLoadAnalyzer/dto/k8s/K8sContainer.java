package com.nla.NeuroLoadAnalyzer.dto.k8s;

/**
 * Container resource / usage snapshot for a K8s workload.
 */
public record K8sContainer(
		String name,
		int cpuMaxPercent,
		int memMaxPercent,
		int cpuAvgPercent,
		int memAvgPercent
) {
}
