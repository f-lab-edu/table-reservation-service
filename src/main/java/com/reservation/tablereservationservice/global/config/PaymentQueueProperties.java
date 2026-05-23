package com.reservation.tablereservationservice.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rabbitmq.payment")
public class PaymentQueueProperties {

	private String exchange;
	private String queue;
	private String routingKey;
	private String dlx;
	private String dlq;
}
