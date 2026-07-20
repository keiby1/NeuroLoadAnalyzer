package com.nla.NeuroLoadAnalyzer.plugin.catalog;

import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPlugin;
import com.nla.NeuroLoadAnalyzer.plugin.AnalysisPluginCatalog;
import com.nla.NeuroLoadAnalyzer.plugin.ThresholdCondition;

import java.util.List;

/**
 * TEMPLATE for local private rules.
 * <p>
 * Copy to:
 * {@code src/main/java/com/nla/NeuroLoadAnalyzer/plugin/catalog/LocalPluginCatalog.java}
 * <p>
 * That file is gitignored and must not be pushed.
 * <p>
 * {@code targetTypePrefix} (e.g. {@code VM}) selects all {@code VM_*} params;
 * {@code $VM} in PromQL is replaced with each parameter value.
 */
public class LocalPluginCatalog implements AnalysisPluginCatalog {

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
						ThresholdCondition.greaterThan(80))
				// add more plugins here
		);
	}
}
