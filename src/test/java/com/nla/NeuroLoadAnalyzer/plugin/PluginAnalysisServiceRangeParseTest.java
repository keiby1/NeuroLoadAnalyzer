package com.nla.NeuroLoadAnalyzer.plugin;

import com.nla.NeuroLoadAnalyzer.dto.PrometheusResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginAnalysisServiceRangeParseTest {

	@Test
	void collectsAndMergesRangeValues() {
		PrometheusResponse.Result r1 = new PrometheusResponse.Result();
		r1.setValues(List.of(
				List.of(100, "10"),
				List.of(200, "20")));
		PrometheusResponse.Result r2 = new PrometheusResponse.Result();
		r2.setValues(List.of(
				List.of(100, "15"),
				List.of(300, "30")));

		PrometheusResponse.DataPayload data = new PrometheusResponse.DataPayload();
		data.setResult(List.of(r1, r2));
		PrometheusResponse response = new PrometheusResponse();
		response.setStatus("success");
		response.setData(data);

		List<MetricPoint> series = PluginAnalysisService.collectRangeSeries(response);
		assertEquals(3, series.size());
		assertEquals(15.0, series.get(0).value()); // max at ts=100
		assertEquals(20.0, series.get(1).value());
		assertEquals(30.0, series.get(2).value());
	}
}
