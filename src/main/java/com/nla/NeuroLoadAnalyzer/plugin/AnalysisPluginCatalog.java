package com.nla.NeuroLoadAnalyzer.plugin;

import java.util.List;

/**
 * Source of analysis plugins. Local catalogs with private rules should not be pushed to git.
 */
public interface AnalysisPluginCatalog {

	List<AnalysisPlugin> getPlugins();
}
