package com.nla.NeuroLoadAnalyzer.controller;

import com.nla.NeuroLoadAnalyzer.dto.AnalysisRequest;
import com.nla.NeuroLoadAnalyzer.service.AnalysisPageService;
import com.nla.NeuroLoadAnalyzer.service.AnalysisService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Entry point for Grafana-style analysis requests.
 * {@code from}/{@code to} are required; any other query params are passed through as variables.
 */
@RestController
public class AnalysisController {

	private final AnalysisService analysisService;
	private final AnalysisPageService analysisPageService;

	public AnalysisController(AnalysisService analysisService, AnalysisPageService analysisPageService) {
		this.analysisService = analysisService;
		this.analysisPageService = analysisPageService;
	}

	/**
	 * Returns an HTML shell with a loading spinner.
	 * The page then requests {@code /analyze/result} with the same query string.
	 */
	@GetMapping(value = "/analyze", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> analyzePage(@RequestParam Map<String, String> allParams) {
		parseRequest(allParams);
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_HTML)
				.body(analysisPageService.loadingPage());
	}

	/**
	 * Runs analysis and returns the result HTML fragment (stub for now).
	 */
	@GetMapping(value = "/analyze/result", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> analyzeResult(@RequestParam Map<String, String> allParams) {
		AnalysisRequest request = parseRequest(allParams);
		String html = analysisService.analyze(request);
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_HTML)
				.body(html);
	}

	private AnalysisRequest parseRequest(Map<String, String> allParams) {
		Long fromMs = parseRequiredLong(allParams, "from");
		Long toMs = parseRequiredLong(allParams, "to");
		if (toMs <= fromMs) {
			throw new ResponseStatusException(BAD_REQUEST, "'to' must be greater than 'from'");
		}

		Map<String, String> variables = new LinkedHashMap<>(allParams);
		variables.remove("from");
		variables.remove("to");
		return new AnalysisRequest(fromMs, toMs, variables);
	}

	private static Long parseRequiredLong(Map<String, String> params, String name) {
		String raw = params.get(name);
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
