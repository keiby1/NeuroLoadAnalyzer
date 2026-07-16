package com.nla.NeuroLoadAnalyzer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PrometheusResponse {

	private String status;
	private String errorType;
	private String error;
	private DataPayload data;

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getErrorType() {
		return errorType;
	}

	public void setErrorType(String errorType) {
		this.errorType = errorType;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public DataPayload getData() {
		return data;
	}

	public void setData(DataPayload data) {
		this.data = data;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DataPayload {
		private String resultType;
		private List<Result> result;

		public String getResultType() {
			return resultType;
		}

		public void setResultType(String resultType) {
			this.resultType = resultType;
		}

		public List<Result> getResult() {
			return result;
		}

		public void setResult(List<Result> result) {
			this.result = result;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Result {
		private Map<String, String> metric;
		/** Instant query: [timestampSec, "value"] */
		private List<Object> value;
		/** Range query: [[timestampSec, "value"], ...] */
		private List<List<Object>> values;

		public Map<String, String> getMetric() {
			return metric;
		}

		public void setMetric(Map<String, String> metric) {
			this.metric = metric;
		}

		public List<Object> getValue() {
			return value;
		}

		public void setValue(List<Object> value) {
			this.value = value;
		}

		public List<List<Object>> getValues() {
			return values;
		}

		public void setValues(List<List<Object>> values) {
			this.values = values;
		}
	}
}
