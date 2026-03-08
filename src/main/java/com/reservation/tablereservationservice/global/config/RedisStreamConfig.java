package com.reservation.tablereservationservice.global.config;

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

	private final StringRedisTemplate redisTemplate;
	private final ReservationStreamProperties streamProperties;

	/**
	 * 애플리케이션 시작 시 파티션 스트림 + Error Stream 의 Consumer Group을 초기화한다.
	 *
	 * ReadOffset.latest(): 그룹 생성 시점 기준
	 *   앱 재시작 후 Consumer는 ReadOffset.lastConsumed()로 구독하므로 이전에 ACK 못한 PEL 메시지를 다시 처리한다.
	 */
	@PostConstruct
	public void initStreamsAndGroups() {
		for (String streamKey : streamProperties.allStreamKeys()) {
			try {
				redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), streamProperties.getConsumerGroup());
				log.info("[RedisStream] Consumer Group 생성 완료: stream={}, group={}", streamKey, streamProperties.getConsumerGroup());
			} catch (Exception e) {
				log.info("[RedisStream] Consumer Group 이미 존재: stream={}", streamKey);
			}
		}

		try {
			redisTemplate.opsForStream().createGroup(streamProperties.getErrorStreamKey(), ReadOffset.latest(), streamProperties.getErrorConsumerGroup());
			log.info("[RedisStream] Error Consumer Group 생성 완료");
		} catch (Exception e) {
			log.info("[RedisStream] Error Consumer Group 이미 존재");
		}
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
