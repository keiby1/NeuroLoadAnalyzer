package com.nla.NeuroLoadAnalyzer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(VictoriaMetricsProperties.class)
public class VictoriaMetricsConfig {

	@Bean
	public RestTemplate victoriaMetricsRestTemplate(VictoriaMetricsProperties properties) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout((int) properties.getConnectTimeoutMs());
		factory.setReadTimeout((int) properties.getReadTimeoutMs());
		return new RestTemplate(factory);
	}
}
