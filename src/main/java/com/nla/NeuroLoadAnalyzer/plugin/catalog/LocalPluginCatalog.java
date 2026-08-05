package com.nla.NeuroLoadAnalyzer.plugin.catalog;

import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPlugin;
import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPluginCatalog;
import com.nla.NeuroLoadAnalyzer.plugin.BandedThresholdCondition;
import com.nla.NeuroLoadAnalyzer.plugin.NonIncreasingTrendCondition;
import com.nla.NeuroLoadAnalyzer.plugin.QuadBandThresholdCondition;
import com.nla.NeuroLoadAnalyzer.plugin.ThresholdCondition;
import com.nla.NeuroLoadAnalyzer.plugin.TrendLeakCondition;
import com.nla.NeuroLoadAnalyzer.plugin.WorkloadMetric;

import java.util.List;

/**
 * LOCAL private plugin definitions — do not commit this file.
 * <p>
 * Rules with {@code targetTypePrefix=VM} run for every {@code VM_*} parameter;
 * {@code $VM} is replaced with that parameter's value.
 * K8S rules run for every {@code k8s_namespace=<name>} parameter.
 */
public class LocalPluginCatalog implements AnalysisPluginCatalog {

	private static final String RAM_USED_BYTES = """
			avg_over_time(
			  (
			    node_memory_MemTotal_bytes{instance=~"$VM"}
			    - node_memory_MemAvailable_bytes{instance=~"$VM"}
			  )[5m:1m]
			)
			""".trim();

	/** Smoothed max CPU %% over from–to: avg window 5m, then max_over_time. */
	private static final String VM_CPU_MAX_5M = """
			max_over_time(
			  (
			    100 - (avg by (instance) (rate(node_cpu_seconds_total{mode="idle", instance=~"$VM"}[5m])) * 100)
			  )[$range:$step]
			)
			""".trim();

	/** Finer max CPU %%: rate window 1m, then max_over_time. */
	private static final String VM_CPU_MAX_1M = """
			max_over_time(
			  (
			    100 - (avg by (instance) (rate(node_cpu_seconds_total{mode="idle", instance=~"$VM"}[1m])) * 100)
			  )[$range:$step]
			)
			""".trim();

	/** Peak RAM %% over from–to (gauge, no rate window). */
	private static final String VM_RAM_MAX = """
			max_over_time(
			  (
			    max by (instance) (
			      100 * (1 - (
			        node_memory_MemAvailable_bytes{instance=~"$VM"}
			        / node_memory_MemTotal_bytes{instance=~"$VM"}
			      ))
			    )
			  )[$range:$step]
			)
			""".trim();

	private static final String K8S_CPU_DOC_5M = """
			max_over_time(rate(container_cpu_usage_seconds_total{container!="",container!="POD",namespace="$namespace"}[5m])[$range:$step])
			/ limits → %; max across pods then containers
			""".trim();

	private static final String K8S_CPU_DOC_1M = """
			max_over_time(rate(container_cpu_usage_seconds_total{container!="",container!="POD",namespace="$namespace"}[1m])[$range:$step])
			/ limits → %; max across pods then containers
			""".trim();

	private static final String K8S_MEM_DOC = """
			max_over_time(container_memory_working_set_bytes{container!="",container!="POD",namespace="$namespace"}[$range:$step])
			/ limits → %; max across pods then containers (no rate smoothing)
			""".trim();

	private static final String K8S_RESTART_DOC = """
			increase(kube_pod_container_status_restarts_total{container!="",container!="POD",namespace="$namespace"}[$range])
			""".trim();

	private static final String K8S_THROTTLING_DOC = """
			avg_over_time(
			  (
			    rate(container_cpu_cfs_throttled_periods_total{container!="",container!="POD",namespace="$namespace"}[5m])
			    / rate(container_cpu_cfs_periods_total{container!="",container!="POD",namespace="$namespace"}[5m])
			    * 100
			  )[$range:1m]
			)
			""".trim();

	private static final String K8S_THROTTLING_TREND_DOC = """
			query_range: (
			  rate(container_cpu_cfs_throttled_periods_total{container!="",container!="POD",namespace="$namespace"}[5m])
			  / rate(container_cpu_cfs_periods_total{container!="",container!="POD",namespace="$namespace"}[5m])
			  * 100
			)  → max by deployment → Sen/MK trend (not increasing)
			""".trim();

	@Override
	public List<AnalysisPlugin> getPlugins() {
		return List.of(
				new AnalysisPlugin(
						"CPU max [5m]",
						"VM",
						VM_CPU_MAX_5M,
						BandedThresholdCondition.warnThenFail(78, 80)),
				new AnalysisPlugin(
						"CPU max [1m]",
						"VM",
						VM_CPU_MAX_1M,
						BandedThresholdCondition.warnThenFail(78, 80)),
				new AnalysisPlugin(
						"RAM usage",
						"VM",
						VM_RAM_MAX,
						BandedThresholdCondition.warnThenFail(78, 80)),
				AnalysisPlugin.range(
						"RAM growth / leak",
						"VM",
						RAM_USED_BYTES,
						TrendLeakCondition.defaults(),
						5),
				new AnalysisPlugin(
						"TCP established",
						"VM",
						"""
						sum(node_tcp_connection_states{state="established", instance=~"$VM"}) or vector(0)
						""".trim(),
						BandedThresholdCondition.infoThenFailInclusive(12_000, 16_000)),
				new AnalysisPlugin(
						"TCP time_wait",
						"VM",
						"""
						sum(node_tcp_connection_states{state="time_wait", instance=~"$VM"}) or vector(0)
						""".trim(),
						BandedThresholdCondition.infoThenFailInclusive(12_000, 16_000)),
				AnalysisPlugin.k8sThreshold(
						"CPU usage [5m]",
						K8S_CPU_DOC_5M,
						BandedThresholdCondition.warnThenFail(78, 80),
						WorkloadMetric.K8S_CPU_MAX_PERCENT),
				AnalysisPlugin.k8sThreshold(
						"CPU usage [1m]",
						K8S_CPU_DOC_1M,
						BandedThresholdCondition.warnThenFail(78, 80),
						WorkloadMetric.K8S_CPU_MAX_PERCENT_1M),
				AnalysisPlugin.k8sThreshold(
						"RAM usage",
						K8S_MEM_DOC,
						BandedThresholdCondition.warnThenFail(78, 80),
						WorkloadMetric.K8S_MEM_MAX_PERCENT),
				AnalysisPlugin.k8sThreshold(
						"Container restarts > 0",
						K8S_RESTART_DOC,
						ThresholdCondition.greaterThan(0),
						WorkloadMetric.K8S_RESTART_INCREASE),
				AnalysisPlugin.k8sThreshold(
						"CPU throttling",
						K8S_THROTTLING_DOC,
						QuadBandThresholdCondition.infoWarnThenFail(1, 3, 7),
						WorkloadMetric.K8S_THROTTLING_MAX_PERCENT),
				AnalysisPlugin.k8sSeries(
						"Throttling trend ↓",
						K8S_THROTTLING_TREND_DOC,
						NonIncreasingTrendCondition.defaults(),
						WorkloadMetric.K8S_THROTTLING_TREND,
						5)
		);
	}
}
