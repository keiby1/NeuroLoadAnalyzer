package com.nla.NeuroLoadAnalyzer.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThresholdConditionTest {

	@Test
	void greaterThanDetectsViolation() {
		ThresholdCondition condition = ThresholdCondition.greaterThan(80);
		assertTrue(condition.isViolation(81));
		assertFalse(condition.isViolation(80));
		assertFalse(condition.isViolation(10));
	}
}
