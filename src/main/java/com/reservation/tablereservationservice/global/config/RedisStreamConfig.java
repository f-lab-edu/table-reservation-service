package com.reservation.tablereservationservice.global.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

	public static final String STREAM_KEY_PREFIX = "reservation:stream.";
	public static final String STREAM_KEY_1 = STREAM_KEY_PREFIX + "1";
	public static final String STREAM_KEY_2 = STREAM_KEY_PREFIX + "2";
	public static final String STREAM_KEY_3 = STREAM_KEY_PREFIX + "3";
	public static final String STREAM_KEY_4 = STREAM_KEY_PREFIX + "4";

	public static final List<String> ALL_STREAM_KEYS = List.of(STREAM_KEY_1, STREAM_KEY_2, STREAM_KEY_3, STREAM_KEY_4);

	// Consumer Group
	public static final String CONSUMER_GROUP = "reservation:consumer-group";
	public static final String CONSUMER_PREFIX = "consumer-";
	public static final int PARTITION_COUNT = 4;

	// Error Stream (DLQ 역할)
	public static final String ERROR_STREAM_KEY = "reservation:error-stream";
	public static final String ERROR_CONSUMER_GROUP = "reservation:error-consumer-group";
	public static final String ERROR_CONSUMER_NAME = "error-consumer-1";

	// Redis 잔여 좌석 카운터 키
	// reservation:remaining:{slotId}:{date}
	public static final String REMAINING_KEY_PREFIX = "reservation:remaining:";

	private final StringRedisTemplate redisTemplate;

	/**
	 * 애플리케이션 시작 시 파티션 스트림 4개 + Error Stream 의 Consumer Group을 초기화한다.
	 *
	 * ReadOffset.latest(): 그룹 생성 시점 기준
	 *   앱 재시작 후 Consumer는 ReadOffset.lastConsumed()로 구독하므로 이전에 ACK 못한 PEL 메시지를 다시 처리한다.
	 */
	@PostConstruct
	public void initStreamsAndGroups() {
		for (String streamKey : ALL_STREAM_KEYS) {
			try {
				redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), CONSUMER_GROUP);
				log.info("[RedisStream] Consumer Group 생성 완료: stream={}, group={}", streamKey, CONSUMER_GROUP);
			} catch (Exception e) {
				log.info("[RedisStream] Consumer Group 이미 존재: stream={}", streamKey);
			}
		}

		try {
			redisTemplate.opsForStream().createGroup(ERROR_STREAM_KEY, ReadOffset.latest(), ERROR_CONSUMER_GROUP);
			log.info("[RedisStream] Error Consumer Group 생성 완료");
		} catch (Exception e) {
			log.info("[RedisStream] Error Consumer Group 이미 존재");
		}
	}

	/**
	 * slotId → 파티션 스트림 키 결정
	 *
	 * 같은 slotId 는 항상 같은 파티션으로 라우팅된다.
	 * → 동일 슬롯의 예약 요청이 단일 Consumer 에게 순서대로 전달되어 선착순이 보장된다.
	 *
	 * 공식: ((slotId - 1) % 4) + 1 → 결과: 1 ~ 4
	 *   slotId=1 → partition 1, slotId=5 → partition 1
	 *   slotId=2 → partition 2, slotId=6 → partition 2  ... (균등 분산)
	 *
	 * RabbitMQ Consistent Hash Exchange 와 동일한 파티셔닝 논리이다.
	 */
	public static String resolveStreamKey(Long slotId) {
		int partition = (int)((slotId - 1) % PARTITION_COUNT) + 1;
		return STREAM_KEY_PREFIX + partition;
	}

	/**
	 * slotId + date 조합으로 Redis 잔여 좌석 카운터 키를 생성한다.
	 * 형식: reservation:remaining:{slotId}:{date}  (예: reservation:remaining:3:2025-07-04)
	 */
	public static String remainingKey(Long slotId, String date) {
		return REMAINING_KEY_PREFIX + slotId + ":" + date;
	}

	/**
	 * StreamMessageListenerContainer: Redis Streams 메시지를 비동기 폴링으로 수신하는 컨테이너
	 * RabbitMQ는 AMOP 프로토콜 자체가 서버->클라이언트 푸시를 지원한다. (Spring이 큐를 감시하다가 푸시)
	 * Redis Stream은 컨테이너가 이 루프를 돌면서 pull 해오는 구조다.
	 *
	 * 내부 동작:
	 *   pollTimeout마다 XREADGROUP 명령을 반복 호출한다.
	 *   새 메시지가 없으면 Redis 는 pollTimeout 동안 blocking 후 빈 응답을 반환한다.
	 *
	 * 주의: 컨테이너는 @Bean 으로 등록만 하고 start() 는 호출하지 않는다.
	 *   ReservationStreamConsumer.run() 에서 모든 파티션 구독을 등록한 뒤 start() 를 호출해야 구독 누락 없이 안전하게 폴링이 시작된다.
	 */
	@Bean
	public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
		RedisConnectionFactory connectionFactory
	) {
		StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options = StreamMessageListenerContainerOptions.builder()
			.pollTimeout(java.time.Duration.ofMillis(100))
			.build();

		return StreamMessageListenerContainer.create(connectionFactory, options);
	}
}
