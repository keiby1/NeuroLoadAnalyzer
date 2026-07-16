package com.nla.NeuroLoadAnalyzer.service;

import com.nla.NeuroLoadAnalyzer.dto.NamedParameter;
import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes Grafana/query variables and extracts {@code Type_Software_Purpose} targets.
 * Duplicate parameter names are preserved as separate targets.
 */
@Component
public class RequestVariableParser {

	private static final Pattern TYPED_NAME = Pattern.compile(
			"^([A-Za-z0-9]+)_([A-Za-z0-9]+)_([A-Za-z0-9]+)$");

	public List<TypedTarget> extractTypedTargets(List<NamedParameter> parameters) {
		if (parameters == null || parameters.isEmpty()) {
			return List.of();
		}
		List<TypedTarget> targets = new ArrayList<>();
		for (NamedParameter parameter : parameters) {
			if (parameter == null || parameter.name() == null || parameter.value() == null) {
				continue;
			}
			String name = stripVarPrefix(parameter.name());
			parseTypedName(name, parameter.value()).ifPresent(targets::add);
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
}
