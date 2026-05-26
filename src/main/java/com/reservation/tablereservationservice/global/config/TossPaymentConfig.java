package com.reservation.tablereservationservice.global.config;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class TossPaymentConfig {

	private final TossPaymentProperties properties;

	@Bean
	public RestClient tossRestClient() {
		String encoded = Base64.getEncoder()
				.encodeToString((properties.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8));

		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
				.build();

		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));

		return RestClient.builder()
				.requestFactory(factory)
				.defaultHeader("Authorization", "Basic " + encoded)
				.defaultHeader("Content-Type", "application/json")
				.build();
	}
}
