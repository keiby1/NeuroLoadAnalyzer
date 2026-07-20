package com.nla.NeuroLoadAnalyzer.service;

import com.nla.NeuroLoadAnalyzer.dto.K8sNamespaceTarget;
import com.nla.NeuroLoadAnalyzer.dto.NamedParameter;
import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes Grafana/query variables and extracts typed targets / k8s namespaces.
 */
@Component
public class RequestVariableParser {

	private static final Pattern TYPED_NAME = Pattern.compile(
			"^([A-Za-z0-9]+)_([A-Za-z0-9]+)_([A-Za-z0-9]+)$");
	private static final String K8S_NAMESPACE_PARAM = "k8s_namespace";

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

	/**
	 * Extracts unique namespaces from {@code k8s_namespace=&lt;name&gt;} parameters
	 * (order preserved; duplicates skipped). Multiple params are supported.
	 */
	public List<K8sNamespaceTarget> extractK8sNamespaces(List<NamedParameter> parameters) {
		if (parameters == null || parameters.isEmpty()) {
			return List.of();
		}
		Set<String> seen = new LinkedHashSet<>();
		List<K8sNamespaceTarget> targets = new ArrayList<>();
		for (NamedParameter parameter : parameters) {
			if (parameter == null || parameter.name() == null || parameter.value() == null) {
				continue;
			}
			String name = stripVarPrefix(parameter.name()).trim();
			if (!K8S_NAMESPACE_PARAM.equalsIgnoreCase(name)) {
				continue;
			}
			String namespace = parameter.value().trim();
			if (namespace.isEmpty() || !seen.add(namespace)) {
				continue;
			}
			targets.add(new K8sNamespaceTarget(name, namespace));
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
