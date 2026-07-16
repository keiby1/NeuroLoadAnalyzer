package com.nla.NeuroLoadAnalyzer.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Incoming analysis request: Grafana time window + arbitrary parameters (multi-value aware).
 */
public final class AnalysisRequest {

	private final Long fromMs;
	private final Long toMs;
	private final List<NamedParameter> parameters;

	public AnalysisRequest(Long fromMs, Long toMs, List<NamedParameter> parameters) {
		this.fromMs = fromMs;
		this.toMs = toMs;
		this.parameters = parameters == null
				? List.of()
				: Collections.unmodifiableList(new ArrayList<>(parameters));
	}

	public Long getFromMs() {
		return fromMs;
	}

	public Long getToMs() {
		return toMs;
	}

	/** All query params except {@code from}/{@code to}; duplicates preserved. */
	public List<NamedParameter> getParameters() {
		return parameters;
	}

	/**
	 * Last-wins map view (for simple lookups). Prefer {@link #getParameters()} for analysis.
	 */
	public Map<String, String> getVariables() {
		Map<String, String> map = new LinkedHashMap<>();
		for (NamedParameter parameter : parameters) {
			map.put(parameter.name(), parameter.value());
		}
		return Collections.unmodifiableMap(map);
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
				&& Objects.equals(parameters, that.parameters);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fromMs, toMs, parameters);
	}

	@Override
	public String toString() {
		return "AnalysisRequest{fromMs=" + fromMs + ", toMs=" + toMs + ", parameters=" + parameters + '}';
	}
}
