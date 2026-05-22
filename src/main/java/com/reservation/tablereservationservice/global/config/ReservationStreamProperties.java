package com.reservation.tablereservationservice.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "redis.reservation")
public class ReservationStreamProperties {

	private String remainingKeyPrefix;
	private String pendingKeyPrefix;
	private int pendingKeyTtlSeconds;

	public String remainingKey(Long slotId, String date) {
		return remainingKeyPrefix + slotId + ":" + date;
	}

	public String pendingKey(Long userId, Long slotId, String date) {
		return pendingKeyPrefix + userId + ":" + slotId + ":" + date;
	}
}
