package com.nla.NeuroLoadAnalyzer.plugin.catalog;

import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPlugin;
import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPluginCatalog;
import com.nla.NeuroLoadAnalyzer.plugin.ThresholdCondition;
import com.nla.NeuroLoadAnalyzer.plugin.TrendLeakCondition;
import com.nla.NeuroLoadAnalyzer.plugin.WorkloadMetric;

import java.util.List;

/**
 * Demo / CI catalog committed to git.
 * VM_* → node_exporter plugins; k8s_namespace=… → OpenShift workload plugins.
 */
public class ExamplePluginCatalog implements AnalysisPluginCatalog {

	private static final String RAM_USED_BYTES = """
			avg_over_time(
			  (
			    node_memory_MemTotal_bytes{instance=~"$VM"}
			    - node_memory_MemAvailable_bytes{instance=~"$VM"}
			  )[5m:1m]
			)
			""".trim();

	private static final String K8S_CPU_DOC = """
			max_over_time(rate(container_cpu_usage_seconds_total{container!="",container!="POD",namespace="$namespace"}[5m])[$range:$step])
			/ limits (sum_then_percent by deployment)
			""".trim();

	private static final String K8S_MEM_DOC = """
			max_over_time(container_memory_working_set_bytes{container!="",container!="POD",namespace="$namespace"}[$range:$step])
			/ limits (sum_then_percent by deployment)
			""".trim();

	@Override
	public List<AnalysisPlugin> getPlugins() {
		return List.of(
				new AnalysisPlugin(
						"CPU > 80%",
						"VM",
						"""
						100 - (avg(irate(node_cpu_seconds_total{mode="idle", instance=~"$VM"}[1m])) by (instance)*100)
						""".trim(),
						ThresholdCondition.greaterThan(80)),
				new AnalysisPlugin(
						"RAM > 80%",
						"VM",
						"""
						max(100*(1-(node_memory_MemAvailable_bytes{instance=~"$VM"} / node_memory_MemTotal_bytes{instance=~"$VM"}))) by (instance)
						""".trim(),
						ThresholdCondition.greaterThan(80)),
				AnalysisPlugin.range(
						"RAM growth / leak",
						"VM",
						RAM_USED_BYTES,
						TrendLeakCondition.defaults(),
						5),
				new AnalysisPlugin(
						"TCP established > 12000",
						"VM",
						"""
						node_tcp_connection_states{state="established", instance=~"$VM"}
						""".trim(),
						ThresholdCondition.greaterThan(12_000)),
				new AnalysisPlugin(
						"TCP time_wait > 12000",
						"VM",
						"""
						node_tcp_connection_states{state="time_wait", instance=~"$VM"}
						""".trim(),
						ThresholdCondition.greaterThan(12_000)),
				AnalysisPlugin.k8sThreshold(
						"CPU usage > 80%",
						K8S_CPU_DOC,
						ThresholdCondition.greaterThan(80),
						WorkloadMetric.K8S_CPU_MAX_PERCENT),
				AnalysisPlugin.k8sThreshold(
						"RAM usage > 80%",
						K8S_MEM_DOC,
						ThresholdCondition.greaterThan(80),
						WorkloadMetric.K8S_MEM_MAX_PERCENT)
		);
	}
}
