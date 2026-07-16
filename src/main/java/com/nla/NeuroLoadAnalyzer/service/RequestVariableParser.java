package com.nla.NeuroLoadAnalyzer.service;

import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes Grafana/query variables and extracts {@code Type_Software_Purpose} targets.
 */
@Component
public class RequestVariableParser {

	private static final Pattern TYPED_NAME = Pattern.compile(
			"^([A-Za-z0-9]+)_([A-Za-z0-9]+)_([A-Za-z0-9]+)$");

	/**
	 * Builds a lookup map: original keys + keys without {@code var-} prefix.
	 */
	public Map<String, String> canonicalVariables(Map<String, String> rawVariables) {
		Map<String, String> result = new LinkedHashMap<>();
		if (rawVariables == null) {
			return result;
		}
		for (Map.Entry<String, String> entry : rawVariables.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if (key == null || value == null) {
				continue;
			}
			result.put(key, value);
			String stripped = stripVarPrefix(key);
			if (!stripped.equals(key)) {
				result.putIfAbsent(stripped, value);
			}
		}
		return Collections.unmodifiableMap(result);
	}

	public List<TypedTarget> extractTypedTargets(Map<String, String> rawVariables) {
		Map<String, String> canonical = canonicalVariables(rawVariables);
		List<TypedTarget> targets = new ArrayList<>();
		for (Map.Entry<String, String> entry : canonical.entrySet()) {
			String name = stripVarPrefix(entry.getKey());
			Optional<TypedTarget> parsed = parseTypedName(name, entry.getValue());
			if (parsed.isPresent() && targets.stream().noneMatch(t -> t.canonicalName().equals(name))) {
				targets.add(parsed.get());
			}
		}
		return List.copyOf(targets);
	}

	public Optional<TypedTarget> parseTypedName(String name, String value) {
		if (name == null || name.isBlank()) {
			return Optional.empty();
		}
		Matcher matcher = TYPED_NAME.matcher(name.trim());
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return Optional.of(new TypedTarget(
				name.trim(),
				matcher.group(1),
				matcher.group(2),
				matcher.group(3),
				value));
	}

	public static String stripVarPrefix(String name) {
		if (name == null) {
			return "";
		}
		String trimmed = name.trim();
		if (trimmed.regionMatches(true, 0, "var-", 0, 4)) {
			return trimmed.substring(4);
		}
		return trimmed;
	}

	public Optional<String> findValue(Map<String, String> canonicalVariables, String placeholderName) {
		if (canonicalVariables == null || placeholderName == null) {
			return Optional.empty();
		}
		String direct = canonicalVariables.get(placeholderName);
		if (direct != null) {
			return Optional.of(direct);
		}
		String stripped = stripVarPrefix(placeholderName);
		String byStripped = canonicalVariables.get(stripped);
		if (byStripped != null) {
			return Optional.of(byStripped);
		}
		// case-insensitive fallback
		for (Map.Entry<String, String> entry : canonicalVariables.entrySet()) {
			if (stripVarPrefix(entry.getKey()).equalsIgnoreCase(stripped)) {
				return Optional.of(entry.getValue());
			}
		}
		return Optional.empty();
	}
}
