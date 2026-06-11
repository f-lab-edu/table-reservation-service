package com.reservation.tablereservationservice.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "outbox")
public class OutboxProperties {

	private int batchSize; // 폴링 한 번에 가져올 최대 PENDING 건수
	private int retentionDays; // PUBLISHED 레코드 보관 기간
}
