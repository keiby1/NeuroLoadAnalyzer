package com.nla.NeuroLoadAnalyzer.plugin;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromQlBinderTest {

	@Test
	void bindsTypePrefixPlaceholder() {
		PromQlBinder.BindResult result = PromQlBinder.bind(
				"up{instance=~\"$VM\"}",
				name -> "VM".equals(name) ? Optional.of("server1") : Optional.empty());

		assertTrue(result.complete());
		assertEquals("up{instance=~\"server1\"}", result.boundQuery());
	}

	@Test
	void keepsGrafanaMultiValueRegex() {
		PromQlBinder.BindResult result = PromQlBinder.bind(
				"up{instance=~\"$VM\"}",
				name -> Optional.of("h1|h2"));

		assertEquals("up{instance=~\"h1|h2\"}", result.boundQuery());
	}
}
