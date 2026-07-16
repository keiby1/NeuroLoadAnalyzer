package com.nla.NeuroLoadAnalyzer.service;

import com.nla.NeuroLoadAnalyzer.dto.NamedParameter;
import com.nla.NeuroLoadAnalyzer.dto.TypedTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestVariableParserTest {

	private final RequestVariableParser parser = new RequestVariableParser();

	@Test
	void extractsTypedTargetsAndStripsVarPrefix() {
		List<NamedParameter> params = List.of(
				new NamedParameter("var-VM_Kafka_GW", "kafka-01:9100"),
				new NamedParameter("custom", "x"));

		List<TypedTarget> targets = parser.extractTypedTargets(params);

		assertEquals(1, targets.size());
		TypedTarget target = targets.get(0);
		assertEquals("VM", target.type());
		assertEquals("Kafka", target.software());
		assertEquals("GW", target.purpose());
		assertEquals("kafka-01:9100", target.value());
	}

	@Test
	void preservesDuplicateParameterNamesAsSeparateTargets() {
		List<NamedParameter> params = List.of(
				new NamedParameter("VM_Kafka_GW", "server1"),
				new NamedParameter("VM_Kafka_GW", "server2"),
				new NamedParameter("VM_Postgre_ASD", "server3"));

		List<TypedTarget> targets = parser.extractTypedTargets(params);

		assertEquals(3, targets.size());
		assertEquals("server1", targets.get(0).value());
		assertEquals("server2", targets.get(1).value());
		assertEquals("Postgre", targets.get(2).software());
	}
}
