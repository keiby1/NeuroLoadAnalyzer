package com.nla.NeuroLoadAnalyzer.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Incoming analysis request: Grafana time window + arbitrary variables/filters.
 */
public final class AnalysisRequest {

	private final Long fromMs;
	private final Long toMs;
	private final Map<String, String> variables;

	public AnalysisRequest(Long fromMs, Long toMs, Map<String, String> variables) {
		this.fromMs = fromMs;
		this.toMs = toMs;
		this.variables = variables == null
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(variables));
	}

	public Long getFromMs() {
		return fromMs;
	}

	public Long getToMs() {
		return toMs;
	}

	/** All query params except {@code from}/{@code to}. */
	public Map<String, String> getVariables() {
		return variables;
	}

	public String getVariable(String name) {
		return variables.get(name);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AnalysisRequest that)) {
			return false;
		}
		return Objects.equals(fromMs, that.fromMs)
				&& Objects.equals(toMs, that.toMs)
				&& Objects.equals(variables, that.variables);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fromMs, toMs, variables);
	}

	@Override
	public String toString() {
		return "AnalysisRequest{fromMs=" + fromMs + ", toMs=" + toMs + ", variables=" + variables + '}';
	}
}
