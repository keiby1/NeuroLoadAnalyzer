package com.nla.NeuroLoadAnalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "victoriametrics")
public class VictoriaMetricsProperties {

	private String baseUrl = "http://localhost:8428";
	private String timeRange = "1h";
	private String subqueryStep = "1m";
	private long connectTimeoutMs = 5_000;
	private long readTimeoutMs = 30_000;

	public String getBaseUrl() {
		if (baseUrl == null) {
			return "";
		}
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getTimeRange() {
		return timeRange;
	}

	public void setTimeRange(String timeRange) {
		this.timeRange = timeRange;
	}

	public String getSubqueryStep() {
		return subqueryStep;
	}

	public void setSubqueryStep(String subqueryStep) {
		this.subqueryStep = subqueryStep;
	}

	public long getConnectTimeoutMs() {
		return connectTimeoutMs;
	}

	public void setConnectTimeoutMs(long connectTimeoutMs) {
		this.connectTimeoutMs = connectTimeoutMs;
	}

	public long getReadTimeoutMs() {
		return readTimeoutMs;
	}

	public void setReadTimeoutMs(long readTimeoutMs) {
		this.readTimeoutMs = readTimeoutMs;
	}
}
