package com.nla.NeuroLoadAnalyzer.dto.k8s;

import java.util.List;

/**
 * Deployment or StatefulSet with container usage metrics.
 */
public record K8sWorkload(
		String namespace,
		String name,
		String workloadType,
		int podCount,
		List<K8sContainer> containers
) {
	public int maxCpuPercent() {
		return containers.stream().mapToInt(K8sContainer::cpuMaxPercent).max().orElse(0);
	}

	public int maxMemPercent() {
		return containers.stream().mapToInt(K8sContainer::memMaxPercent).max().orElse(0);
	}
}
