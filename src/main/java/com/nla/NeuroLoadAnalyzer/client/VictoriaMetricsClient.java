package com.nla.NeuroLoadAnalyzer.client;

import com.nla.NeuroLoadAnalyzer.config.VictoriaMetricsProperties;
import com.nla.NeuroLoadAnalyzer.dto.PrometheusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Thin transport client for VictoriaMetrics Prometheus-compatible HTTP API.
 * Domain PromQL and aggregation live in higher-level services.
 */
@Component
public class VictoriaMetricsClient {

	private static final Logger log = LoggerFactory.getLogger(VictoriaMetricsClient.class);

	private final RestTemplate restTemplate;
	private final VictoriaMetricsProperties properties;

	public VictoriaMetricsClient(
			@Qualifier("victoriaMetricsRestTemplate") RestTemplate restTemplate,
			VictoriaMetricsProperties properties) {
		this.restTemplate = restTemplate;
		this.properties = properties;
	}

	/**
	 * Instant query: GET /api/v1/query
	 *
	 * @param query    PromQL expression
	 * @param timeSec  evaluation time in Unix seconds; {@code null} = now on VM side
	 */
	public PrometheusResponse query(String query, Long timeSec) {
		UriComponentsBuilder builder = UriComponentsBuilder
				.fromUriString(properties.getBaseUrl() + "/api/v1/query")
				.queryParam("query", query);
		if (timeSec != null) {
			builder.queryParam("time", timeSec);
		}
		return exchange(builder.build().encode().toUri(), query);
	}

	/**
	 * Range query: GET /api/v1/query_range
	 * Reserved for cases that need full series (e.g. trend / leak detection).
	 */
	public PrometheusResponse queryRange(String query, long startSec, long endSec, long stepSec) {
		URI uri = UriComponentsBuilder
				.fromUriString(properties.getBaseUrl() + "/api/v1/query_range")
				.queryParam("query", query)
				.queryParam("start", startSec)
				.queryParam("end", endSec)
				.queryParam("step", stepSec)
				.build()
				.encode()
				.toUri();
		return exchange(uri, query);
	}

	public static double parseValue(List<Object> value) {
		if (value == null || value.size() < 2 || value.get(1) == null) {
			return Double.NaN;
		}
		try {
			return Double.parseDouble(value.get(1).toString());
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	public static String getLabel(Map<String, String> metric, String label) {
		if (metric == null || label == null) {
			return null;
		}
		return metric.get(label);
	}

	private PrometheusResponse exchange(URI uri, String query) {
		try {
			log.debug("VM request: {}", uri);
			PrometheusResponse response = restTemplate.getForObject(uri, PrometheusResponse.class);
			if (response == null) {
				throw new RestClientException("VictoriaMetrics returned empty body");
			}
			if (!"success".equalsIgnoreCase(response.getStatus())) {
				log.warn("VM query failed: status={}, errorType={}, error={}, query={}",
						response.getStatus(), response.getErrorType(), response.getError(), shorten(query));
			}
			return response;
		} catch (RestClientException e) {
			log.error("VM request error: url={}, query={}, message={}",
					uri, shorten(query), e.getMessage());
			throw e;
		}
	}

	private static String shorten(String query) {
		if (query == null) {
			return "";
		}
		return query.length() <= 200 ? query : query.substring(0, 200) + "...";
	}
}
