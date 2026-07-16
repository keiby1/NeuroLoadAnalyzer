package com.nla.NeuroLoadAnalyzer.controller;

import com.nla.NeuroLoadAnalyzer.dto.AnalysisRequest;
import com.nla.NeuroLoadAnalyzer.dto.NamedParameter;
import com.nla.NeuroLoadAnalyzer.service.AnalysisPageService;
import com.nla.NeuroLoadAnalyzer.service.AnalysisService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Entry point for Grafana-style analysis requests.
 * {@code from}/{@code to} are required; any other query params are passed through (multi-value OK).
 */
@RestController
public class AnalysisController {

	private final AnalysisService analysisService;
	private final AnalysisPageService analysisPageService;

	public AnalysisController(AnalysisService analysisService, AnalysisPageService analysisPageService) {
		this.analysisService = analysisService;
		this.analysisPageService = analysisPageService;
	}

	@GetMapping(value = "/analyze", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> analyzePage(@RequestParam MultiValueMap<String, String> allParams) {
		parseRequest(allParams);
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_HTML)
				.body(analysisPageService.loadingPage());
	}

	@GetMapping(value = "/analyze/result", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> analyzeResult(@RequestParam MultiValueMap<String, String> allParams) {
		AnalysisRequest request = parseRequest(allParams);
		String html = analysisService.analyze(request);
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_HTML)
				.body(html);
	}

	private AnalysisRequest parseRequest(MultiValueMap<String, String> allParams) {
		Long fromMs = parseRequiredLong(allParams, "from");
		Long toMs = parseRequiredLong(allParams, "to");
		if (toMs <= fromMs) {
			throw new ResponseStatusException(BAD_REQUEST, "'to' must be greater than 'from'");
		}

		List<NamedParameter> parameters = new ArrayList<>();
		if (allParams != null) {
			for (String name : allParams.keySet()) {
				if ("from".equals(name) || "to".equals(name)) {
					continue;
				}
				List<String> values = allParams.get(name);
				if (values == null) {
					continue;
				}
				for (String value : values) {
					if (value != null) {
						parameters.add(new NamedParameter(name, value));
					}
				}
			}
		}
		return new AnalysisRequest(fromMs, toMs, parameters);
	}

	private static Long parseRequiredLong(MultiValueMap<String, String> params, String name) {
		String raw = params == null ? null : params.getFirst(name);
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(BAD_REQUEST, "Required query parameter '" + name + "' is missing");
		}
		try {
			return Long.parseLong(raw.trim());
		} catch (NumberFormatException e) {
			throw new ResponseStatusException(BAD_REQUEST,
					"Query parameter '" + name + "' must be a Unix timestamp in milliseconds");
		}
	}
}
