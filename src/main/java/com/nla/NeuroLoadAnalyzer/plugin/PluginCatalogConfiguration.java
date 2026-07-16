package com.nla.NeuroLoadAnalyzer.plugin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Prefers {@code LocalPluginCatalog} (gitignored private rules) when the class is on the classpath;
 * otherwise falls back to {@link com.nla.NeuroLoadAnalyzer.plugin.catalog.ExamplePluginCatalog}.
 */
@Configuration
public class PluginCatalogConfiguration {

	private static final String LOCAL_CATALOG =
			"com.nla.NeuroLoadAnalyzer.plugin.catalog.LocalPluginCatalog";

	@Bean
	public AnalysisPluginCatalog analysisPluginCatalog() {
		AnalysisPluginCatalog local = tryLoadLocalCatalog();
		if (local != null) {
			return local;
		}
		return new com.nla.NeuroLoadAnalyzer.plugin.catalog.ExamplePluginCatalog();
	}

	private static AnalysisPluginCatalog tryLoadLocalCatalog() {
		try {
			Class<?> clazz = Class.forName(LOCAL_CATALOG);
			Object instance = clazz.getDeclaredConstructor().newInstance();
			if (instance instanceof AnalysisPluginCatalog catalog) {
				return catalog;
			}
			return null;
		} catch (ClassNotFoundException e) {
			return null;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to instantiate LocalPluginCatalog", e);
		}
	}
}
