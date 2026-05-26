package com.reservation.tablereservationservice.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "toss.payments")
public class TossPaymentProperties {

	private String secretKey;
	private String confirmUrl;
	private String paymentBaseUrl;
	private int connectTimeoutSeconds;
	private int readTimeoutSeconds;
}
