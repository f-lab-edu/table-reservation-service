package com.reservation.tablereservationservice.global.config;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "redis.stream.reservation")
public class ReservationStreamProperties {

	private String keyPrefix;
	private int partitionCount;
	private String consumerGroup;
	private String consumerPrefix;
	private String errorStreamKey;
	private String errorConsumerGroup;
	private String errorConsumerName;
	private String remainingKeyPrefix;

	/**
	 * 파티션 수 기반으로 모든 스트림 키를 동적으로 생성한다.
	 */
	public List<String> allStreamKeys() {
		return IntStream.rangeClosed(1, partitionCount)
			.mapToObj(i -> keyPrefix + i)
			.toList();
	}

	/**
	 * slotId → 파티션 스트림 키 결정
	 * 같은 slotId는 항상 같은 파티션으로 라우팅된다.
	 */
	public String resolveStreamKey(Long slotId) {
		int partition = (int)((slotId - 1) % partitionCount) + 1;
		return keyPrefix + partition;
	}

	/**
	 * slotId + date 조합으로 Redis 잔여 좌석 카운터 키를 생성한다.
	 * 형식: reservation:remaining:{slotId}:{date}
	 */
	public String remainingKey(Long slotId, String date) {
		return remainingKeyPrefix + slotId + ":" + date;
	}
}
