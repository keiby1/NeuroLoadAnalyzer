package com.nla.NeuroLoadAnalyzer.plugin.catalog;

import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPlugin;
import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPluginCatalog;
import com.nla.NeuroLoadAnalyzer.plugin.ThresholdCondition;

import java.util.List;

/**
 * Demo / CI catalog committed to git.
 * For private rules copy {@code LocalPluginCatalog.example.java} → {@code LocalPluginCatalog.java}
 * (the latter is gitignored).
 */
public class ExamplePluginCatalog implements AnalysisPluginCatalog {

	@Override
	public List<AnalysisPlugin> getPlugins() {
		return List.of(
				new AnalysisPlugin(
						"CPU Kafka",
						"""
						100 - (avg(irate(node_cpu_seconds_total{mode="idle", instance=~"$VM_Kafka_GW"}[1m])) by (instance) * 100)
						""".trim(),
						ThresholdCondition.greaterThan(80)),
				new AnalysisPlugin(
						"RAM Kafka",
						"""
						max(100 * (1 - (node_memory_MemAvailable_bytes{instance=~"$VM_Kafka_GW"} / node_memory_MemTotal_bytes{instance=~"$VM_Kafka_GW"}))) by (instance)
						""".trim(),
						ThresholdCondition.greaterThan(80))
		);
	}
}
