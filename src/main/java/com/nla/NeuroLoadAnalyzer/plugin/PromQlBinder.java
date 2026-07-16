package com.nla.NeuroLoadAnalyzer.plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Binds {@code $ParamName} placeholders in PromQL templates to request variables.
 */
public final class PromQlBinder {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

	private PromQlBinder() {
	}

	public static Set<String> extractPlaceholders(String promQlTemplate) {
		Set<String> names = new LinkedHashSet<>();
		if (promQlTemplate == null) {
			return names;
		}
		Matcher matcher = PLACEHOLDER.matcher(promQlTemplate);
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		return names;
	}

	public static BindResult bind(
			String promQlTemplate,
			java.util.function.Function<String, Optional<String>> valueResolver) {
		Set<String> placeholders = extractPlaceholders(promQlTemplate);
		List<String> missing = new ArrayList<>();
		StringBuffer bound = new StringBuffer();
		Matcher matcher = PLACEHOLDER.matcher(promQlTemplate == null ? "" : promQlTemplate);
		while (matcher.find()) {
			String name = matcher.group(1);
			Optional<String> value = valueResolver.apply(name);
			if (value.isEmpty()) {
				missing.add(name);
				matcher.appendReplacement(bound, Matcher.quoteReplacement(matcher.group(0)));
			} else {
				// Keep Grafana multi-value regex intact (host1|host2); only escape PromQL string delimiters.
				matcher.appendReplacement(bound, Matcher.quoteReplacement(escapePromQlLiteral(value.get())));
			}
		}
		matcher.appendTail(bound);
		return new BindResult(bound.toString(), List.copyOf(placeholders), List.copyOf(missing));
	}

	/** Escapes characters that would break a PromQL double-quoted literal. */
	public static String escapePromQlLiteral(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	public record BindResult(String boundQuery, List<String> placeholders, List<String> missingPlaceholders) {
		public boolean complete() {
			return missingPlaceholders.isEmpty();
		}
	}
}
