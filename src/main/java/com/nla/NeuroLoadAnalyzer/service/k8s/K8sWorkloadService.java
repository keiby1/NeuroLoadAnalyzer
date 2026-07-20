package com.nla.NeuroLoadAnalyzer.service.k8s;

import com.nla.NeuroLoadAnalyzer.client.VictoriaMetricsClient;
import com.nla.NeuroLoadAnalyzer.config.VictoriaMetricsProperties;
import com.nla.NeuroLoadAnalyzer.dto.PrometheusResponse;
import com.nla.NeuroLoadAnalyzer.dto.k8s.K8sContainer;
import com.nla.NeuroLoadAnalyzer.dto.k8s.K8sWorkload;
import com.nla.NeuroLoadAnalyzer.util.TimeRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Collects OpenShift/K8s Deployment & StatefulSet workloads with container CPU/RAM usage.
 * Logic adapted from the reference VictoriaMetricsService (ZK_tablecreater).
 */
@Service
public class K8sWorkloadService {

	private static final Logger log = LoggerFactory.getLogger(K8sWorkloadService.class);

	private static final String LABEL_NAMESPACE = "namespace";
	private static final String LABEL_DEPLOYMENT = "deployment";
	private static final String LABEL_STATEFULSET = "statefulset";
	private static final String LABEL_POD = "pod";
	private static final String LABEL_CONTAINER = "container";
	private static final String LABEL_OWNER_NAME = "owner_name";
	private static final String LABEL_REPLICASET = "replicaset";

	private final VictoriaMetricsClient client;
	private final VictoriaMetricsProperties properties;

	public K8sWorkloadService(VictoriaMetricsClient client, VictoriaMetricsProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	public List<K8sWorkload> fetchWorkloads(String namespace, TimeRange timeRange) {
		String range = timeRange.rangeForPromQl();
		Long evaluationTimeSec = timeRange.evaluationTimeSec();

		Map<String, Integer> deploymentReplicas = queryDeploymentReplicas(namespace, evaluationTimeSec);
		Map<String, Integer> statefulSetReplicas = queryStatefulSetReplicas(namespace, evaluationTimeSec);
		Map<String, String> podToDeployment = queryPodToDeployment(namespace, evaluationTimeSec);
		Map<String, String> podToStatefulSet = queryPodToStatefulSet(namespace, evaluationTimeSec);

		if (podToDeployment.isEmpty() && deploymentReplicas.isEmpty()
				&& podToStatefulSet.isEmpty() && statefulSetReplicas.isEmpty()) {
			log.info("K8S namespace='{}': no deployments/statefulsets found", namespace);
			return List.of();
		}

		Map<ContainerKey, ResourceValues> resources = queryContainerResources(namespace, evaluationTimeSec);
		Map<ContainerKey, UsageValues> usage = queryContainerUsage(range, namespace, evaluationTimeSec);

		List<K8sWorkload> result = new ArrayList<>();

		Map<DeploymentKey, List<ContainerKey>> deploymentToContainers = new HashMap<>();
		for (ContainerKey ck : resources.keySet()) {
			String dep = podToDeployment.get(ck.namespace + "/" + ck.pod);
			if (dep == null) {
				continue;
			}
			deploymentToContainers.computeIfAbsent(new DeploymentKey(ck.namespace, dep), k -> new ArrayList<>()).add(ck);
		}
		for (String nsAndDep : deploymentReplicas.keySet()) {
			String[] parts = nsAndDep.split("/", 2);
			if (parts.length != 2) {
				continue;
			}
			deploymentToContainers.putIfAbsent(new DeploymentKey(parts[0], parts[1]), new ArrayList<>());
		}
		for (Map.Entry<DeploymentKey, List<ContainerKey>> e : deploymentToContainers.entrySet()) {
			DeploymentKey dk = e.getKey();
			int podCount = deploymentReplicas.getOrDefault(dk.namespace + "/" + dk.name, 0);
			if (podCount == 0) {
				continue;
			}
			K8sWorkload workload = buildWorkload(dk.namespace, dk.name, "Deployment", podCount, e.getValue(), resources, usage);
			if (workload != null) {
				result.add(workload);
			}
		}

		Map<DeploymentKey, List<ContainerKey>> stsToContainers = new HashMap<>();
		for (ContainerKey ck : resources.keySet()) {
			String sts = podToStatefulSet.get(ck.namespace + "/" + ck.pod);
			if (sts == null) {
				continue;
			}
			stsToContainers.computeIfAbsent(new DeploymentKey(ck.namespace, sts), k -> new ArrayList<>()).add(ck);
		}
		for (String nsAndSts : statefulSetReplicas.keySet()) {
			String[] parts = nsAndSts.split("/", 2);
			if (parts.length != 2) {
				continue;
			}
			stsToContainers.putIfAbsent(new DeploymentKey(parts[0], parts[1]), new ArrayList<>());
		}
		for (Map.Entry<DeploymentKey, List<ContainerKey>> e : stsToContainers.entrySet()) {
			DeploymentKey dk = e.getKey();
			int podCount = statefulSetReplicas.getOrDefault(dk.namespace + "/" + dk.name, 0);
			if (podCount == 0) {
				continue;
			}
			K8sWorkload workload = buildWorkload(dk.namespace, dk.name, "StatefulSet", podCount, e.getValue(), resources, usage);
			if (workload != null) {
				result.add(workload);
			}
		}

		result.sort(Comparator.comparing(K8sWorkload::workloadType).thenComparing(K8sWorkload::name));
		log.info("K8S namespace='{}': workloads={}", namespace, result.size());
		return List.copyOf(result);
	}

	private K8sWorkload buildWorkload(
			String namespace,
			String name,
			String workloadType,
			int podCount,
			List<ContainerKey> containerKeys,
			Map<ContainerKey, ResourceValues> resources,
			Map<ContainerKey, UsageValues> usage) {
		Map<String, List<ContainerKey>> byContainer = containerKeys.stream()
				.collect(Collectors.groupingBy(ck -> ck.container));
		List<K8sContainer> containers = new LinkedList<>();
		for (Map.Entry<String, List<ContainerKey>> ce : byContainer.entrySet()) {
			containers.add(buildContainer(ce.getKey(), ce.getValue(), resources, usage));
		}
		if (containers.isEmpty()) {
			return null;
		}
		return new K8sWorkload(namespace, name, workloadType, podCount, List.copyOf(containers));
	}

	private K8sContainer buildContainer(
			String containerName,
			List<ContainerKey> keys,
			Map<ContainerKey, ResourceValues> resources,
			Map<ContainerKey, UsageValues> usage) {
		boolean sumThenPercent = "sum_then_percent".equalsIgnoreCase(properties.getAggregationMethod());
		int cpuAvgPercent;
		int cpuMaxPercent;
		int memAvgPercent;
		int memMaxPercent;

		if (sumThenPercent) {
			double sumCpuAvg = 0, sumMemAvg = 0, sumCpuLim = 0, sumMemLim = 0;
			double cpuMaxPct = 0, memMaxPct = 0;
			for (ContainerKey ck : keys) {
				UsageValues uv = usage.get(ck);
				ResourceValues res = resources.get(ck);
				if (uv == null) {
					continue;
				}
				double cpuLim = res != null ? res.cpuLimCores : 0;
				double memLim = res != null ? res.memLimBytes : 0;
				if (cpuLim <= 0) {
					cpuLim = res != null ? res.cpuRqCores : 0;
				}
				if (memLim <= 0) {
					memLim = res != null ? res.memRqBytes : 0;
				}
				sumCpuAvg += uv.cpuAvgCores;
				sumMemAvg += uv.memAvgBytes;
				sumCpuLim += cpuLim;
				sumMemLim += memLim;
				cpuMaxPct = Math.max(cpuMaxPct, toPercent(uv.cpuMaxCores, cpuLim));
				memMaxPct = Math.max(memMaxPct, toPercent(uv.memMaxBytes, memLim));
			}
			cpuAvgPercent = sumCpuLim > 0 ? (int) Math.round(toPercent(sumCpuAvg, sumCpuLim)) : 0;
			cpuMaxPercent = (int) Math.round(cpuMaxPct);
			memAvgPercent = sumMemLim > 0 ? (int) Math.round(toPercent(sumMemAvg, sumMemLim)) : 0;
			memMaxPercent = (int) Math.round(memMaxPct);
		} else {
			double cpuAvgSum = 0, cpuMaxMax = 0, memAvgSum = 0, memMaxMax = 0;
			int n = 0;
			for (ContainerKey ck : keys) {
				UsageValues uv = usage.get(ck);
				if (uv == null) {
					continue;
				}
				ResourceValues res = resources.get(ck);
				double cpuLim = res != null ? res.cpuLimCores : 0;
				double memLim = res != null ? res.memLimBytes : 0;
				if (cpuLim <= 0) {
					cpuLim = res != null ? res.cpuRqCores : 0;
				}
				if (memLim <= 0) {
					memLim = res != null ? res.memRqBytes : 0;
				}
				cpuAvgSum += toPercent(uv.cpuAvgCores, cpuLim);
				cpuMaxMax = Math.max(cpuMaxMax, toPercent(uv.cpuMaxCores, cpuLim));
				memAvgSum += toPercent(uv.memAvgBytes, memLim);
				memMaxMax = Math.max(memMaxMax, toPercent(uv.memMaxBytes, memLim));
				n++;
			}
			cpuAvgPercent = n > 0 ? (int) Math.round(cpuAvgSum / n) : 0;
			cpuMaxPercent = (int) Math.round(cpuMaxMax);
			memAvgPercent = n > 0 ? (int) Math.round(memAvgSum / n) : 0;
			memMaxPercent = (int) Math.round(memMaxMax);
		}

		return new K8sContainer(containerName, cpuMaxPercent, memMaxPercent, cpuAvgPercent, memAvgPercent);
	}

	private static String addNamespaceFilter(String query, String namespace) {
		if (namespace == null || namespace.isBlank()) {
			return query;
		}
		String escaped = namespace.replace("\\", "\\\\").replace("\"", "\\\"");
		int lastBrace = query.lastIndexOf('}');
		if (lastBrace >= 0) {
			return query.substring(0, lastBrace) + ",namespace=\"" + escaped + "\"}" + query.substring(lastBrace + 1);
		}
		return query + "{namespace=\"" + escaped + "\"}";
	}

	private Map<String, Integer> queryDeploymentReplicas(String namespace, Long evaluationTimeSec) {
		String query = addNamespaceFilter("kube_deployment_status_replicas_available", namespace);
		PrometheusResponse resp = client.query(query, evaluationTimeSec);
		Map<String, Integer> out = new HashMap<>();
		if (resp == null || resp.getData() == null || resp.getData().getResult() == null) {
			return out;
		}
		for (PrometheusResponse.Result r : resp.getData().getResult()) {
			String ns = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_NAMESPACE);
			String dep = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_DEPLOYMENT);
			if (ns == null || dep == null) {
				continue;
			}
			double v = VictoriaMetricsClient.parseValue(r.getValue());
			if (!Double.isNaN(v)) {
				out.put(ns + "/" + dep, (int) Math.round(v));
			}
		}
		return out;
	}

	private Map<String, Integer> queryStatefulSetReplicas(String namespace, Long evaluationTimeSec) {
		String query = addNamespaceFilter("kube_statefulset_status_replicas_ready", namespace);
		PrometheusResponse resp = client.query(query, evaluationTimeSec);
		Map<String, Integer> out = new HashMap<>();
		if (resp == null || resp.getData() == null || resp.getData().getResult() == null) {
			return out;
		}
		for (PrometheusResponse.Result r : resp.getData().getResult()) {
			String ns = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_NAMESPACE);
			String sts = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_STATEFULSET);
			if (ns == null || sts == null) {
				continue;
			}
			double v = VictoriaMetricsClient.parseValue(r.getValue());
			if (!Double.isNaN(v)) {
				out.put(ns + "/" + sts, (int) Math.round(v));
			}
		}
		return out;
	}

	private Map<String, String> queryPodToDeployment(String namespace, Long evaluationTimeSec) {
		String qPod = addNamespaceFilter("kube_pod_owner{owner_kind=\"ReplicaSet\"}", namespace);
		PrometheusResponse respPod = client.query(qPod, evaluationTimeSec);
		if (respPod == null || respPod.getData() == null || respPod.getData().getResult() == null) {
			return Map.of();
		}

		Map<String, String> podToReplicaset = new HashMap<>();
		for (PrometheusResponse.Result r : respPod.getData().getResult()) {
			String ns = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_NAMESPACE);
			String pod = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_POD);
			String rs = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_OWNER_NAME);
			if (ns != null && pod != null && rs != null) {
				podToReplicaset.put(ns + "/" + pod, rs);
			}
		}

		String qRS = addNamespaceFilter("kube_replicaset_owner", namespace);
		PrometheusResponse respRS = client.query(qRS, evaluationTimeSec);
		Map<String, String> replicasetToDeployment = new HashMap<>();
		if (respRS != null && respRS.getData() != null && respRS.getData().getResult() != null) {
			for (PrometheusResponse.Result r : respRS.getData().getResult()) {
				String ns = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_NAMESPACE);
				String rs = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_REPLICASET);
				String dep = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_OWNER_NAME);
				if (ns != null && rs != null && dep != null) {
					replicasetToDeployment.put(ns + "/" + rs, dep);
				}
			}
		}

		Map<String, String> podToDeployment = new HashMap<>();
		for (Map.Entry<String, String> e : podToReplicaset.entrySet()) {
			String ns = e.getKey().substring(0, e.getKey().indexOf('/'));
			String dep = replicasetToDeployment.get(ns + "/" + e.getValue());
			if (dep != null) {
				podToDeployment.put(e.getKey(), dep);
			}
		}
		return podToDeployment;
	}

	private Map<String, String> queryPodToStatefulSet(String namespace, Long evaluationTimeSec) {
		String qPod = addNamespaceFilter("kube_pod_owner{owner_kind=\"StatefulSet\"}", namespace);
		PrometheusResponse respPod = client.query(qPod, evaluationTimeSec);
		Map<String, String> podToStatefulSet = new HashMap<>();
		if (respPod == null || respPod.getData() == null || respPod.getData().getResult() == null) {
			return podToStatefulSet;
		}
		for (PrometheusResponse.Result r : respPod.getData().getResult()) {
			String ns = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_NAMESPACE);
			String pod = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_POD);
			String sts = VictoriaMetricsClient.getLabel(r.getMetric(), LABEL_OWNER_NAME);
			if (ns != null && pod != null && sts != null) {
				podToStatefulSet.put(ns + "/" + pod, sts);
			}
		}
		return podToStatefulSet;
	}

	private Map<ContainerKey, ResourceValues> queryContainerResources(String namespace, Long evaluationTimeSec) {
		Map<ContainerKey, ResourceValues> out = new HashMap<>();
		fillResourceMetric(out, addNamespaceFilter("kube_pod_container_resource_requests{resource=\"cpu\"}", namespace),
				(rv, v) -> rv.cpuRqCores = v, evaluationTimeSec);
		fillResourceMetric(out, addNamespaceFilter("kube_pod_container_resource_limits{resource=\"cpu\"}", namespace),
				(rv, v) -> rv.cpuLimCores = v, evaluationTimeSec);
		fillResourceMetric(out, addNamespaceFilter("kube_pod_container_resource_requests{resource=\"memory\"}", namespace),
				(rv, v) -> rv.memRqBytes = v, evaluationTimeSec);
		fillResourceMetric(out, addNamespaceFilter("kube_pod_container_resource_limits{resource=\"memory\"}", namespace),
				(rv, v) -> rv.memLimBytes = v, evaluationTimeSec);
		return out;
	}

	private void fillResourceMetric(
			Map<ContainerKey, ResourceValues> out,
			String query,
			java.util.function.BiConsumer<ResourceValues, Double> setter,
			Long evaluationTimeSec) {
		PrometheusResponse resp = client.query(query, evaluationTimeSec);
		if (resp == null || resp.getData() == null || resp.getData().getResult() == null) {
			return;
		}
		for (PrometheusResponse.Result r : resp.getData().getResult()) {
			ContainerKey ck = containerKeyFromMetric(r.getMetric());
			if (ck == null) {
				continue;
			}
			double v = VictoriaMetricsClient.parseValue(r.getValue());
			if (Double.isNaN(v)) {
				continue;
			}
			out.computeIfAbsent(ck, k -> new ResourceValues()).apply(setter, v);
		}
	}

	private Map<ContainerKey, UsageValues> queryContainerUsage(String range, String namespace, Long evaluationTimeSec) {
		Map<ContainerKey, UsageValues> out = new HashMap<>();
		String step = properties.getSubqueryStep();
		String cpuWindow = properties.getCpuRateWindow();

		String cpuRate = addNamespaceFilter(
				"rate(container_cpu_usage_seconds_total{container!=\"\",container!=\"POD\"}[" + cpuWindow + "])",
				namespace);
		fillUsageMetric(out, "avg_over_time(" + cpuRate + "[" + range + ":" + step + "])",
				(uv, v) -> uv.cpuAvgCores = v, evaluationTimeSec);
		fillUsageMetric(out, "max_over_time(" + cpuRate + "[" + range + ":" + step + "])",
				(uv, v) -> uv.cpuMaxCores = v, evaluationTimeSec);

		String mem = addNamespaceFilter(
				"container_memory_working_set_bytes{container!=\"\",container!=\"POD\"}",
				namespace);
		fillUsageMetric(out, "avg_over_time(" + mem + "[" + range + ":" + step + "])",
				(uv, v) -> uv.memAvgBytes = v, evaluationTimeSec);
		fillUsageMetric(out, "max_over_time(" + mem + "[" + range + ":" + step + "])",
				(uv, v) -> uv.memMaxBytes = v, evaluationTimeSec);
		return out;
	}

	private void fillUsageMetric(
			Map<ContainerKey, UsageValues> out,
			String query,
			java.util.function.BiConsumer<UsageValues, Double> setter,
			Long evaluationTimeSec) {
		PrometheusResponse resp = client.query(query, evaluationTimeSec);
		if (resp == null || resp.getData() == null || resp.getData().getResult() == null) {
			return;
		}
		for (PrometheusResponse.Result r : resp.getData().getResult()) {
			ContainerKey ck = containerKeyFromMetric(r.getMetric());
			if (ck == null) {
				continue;
			}
			double v = VictoriaMetricsClient.parseValue(r.getValue());
			if (Double.isNaN(v)) {
				continue;
			}
			out.computeIfAbsent(ck, k -> new UsageValues()).apply(setter, v);
		}
	}

	private ContainerKey containerKeyFromMetric(Map<String, String> metric) {
		String ns = VictoriaMetricsClient.getLabel(metric, LABEL_NAMESPACE);
		String pod = VictoriaMetricsClient.getLabel(metric, LABEL_POD);
		String container = VictoriaMetricsClient.getLabel(metric, LABEL_CONTAINER);
		if (ns == null || pod == null || container == null || container.isEmpty() || "POD".equals(container)) {
			return null;
		}
		return new ContainerKey(ns, pod, container);
	}

	private static double toPercent(double used, double limit) {
		if (limit <= 0) {
			return 0;
		}
		return used / limit * 100;
	}

	private record ContainerKey(String namespace, String pod, String container) {
	}

	private record DeploymentKey(String namespace, String name) {
	}

	private static class ResourceValues {
		double cpuRqCores, cpuLimCores, memRqBytes, memLimBytes;

		void apply(java.util.function.BiConsumer<ResourceValues, Double> setter, double v) {
			setter.accept(this, v);
		}
	}

	private static class UsageValues {
		double cpuAvgCores, cpuMaxCores, memAvgBytes, memMaxBytes;

		void apply(java.util.function.BiConsumer<UsageValues, Double> setter, double v) {
			setter.accept(this, v);
		}
	}
}
