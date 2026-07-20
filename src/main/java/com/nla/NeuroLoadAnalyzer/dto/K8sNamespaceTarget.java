package com.nla.NeuroLoadAnalyzer.dto;

/**
 * Incoming {@code k8s_namespace=&lt;name&gt;} parameter (namespace is the parameter value).
 */
public record K8sNamespaceTarget(String parameterName, String namespace) {
}
