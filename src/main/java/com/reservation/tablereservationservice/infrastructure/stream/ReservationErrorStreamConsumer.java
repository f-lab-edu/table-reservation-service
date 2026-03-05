package com.reservation.tablereservationservice.infrastructure.stream;

import static com.reservation.tablereservationservice.global.config.RedisStreamConfig.*;

import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Error Stream Consumer — RabbitMQ DLQ Consumer 역할
 *
 * MAX_RETRY 초과로 Error Stream에 이동된 메시지를 소비하고 로그를 남긴다.
 * 현재는 로깅만 수행. 향후 재처리 등으로 확장 가능
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationErrorStreamConsumer implements ApplicationRunner {

	private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
	private final StringRedisTemplate redisTemplate;

	@Override
	public void run(ApplicationArguments args) {
		listenerContainer.receive(
			Consumer.from(ERROR_CONSUMER_GROUP, ERROR_CONSUMER_NAME),
			StreamOffset.create(ERROR_STREAM_KEY, ReadOffset.lastConsumed()),
			this::handle
		);
	}

	/**
	 * Error Stream 메시지 처리
	 *
	 * 메시지 필드:
	 *   - userEmail, slotId, date, partySize, note: 원본 예약 요청
	 *   - originalStream: 메시지가 원래 있던 파티션 스트림 키
	 *   - originalId:     원본 스트림에서의 메시지 ID
	 *   - deliveryCount:  재처리 시도 횟수
	 *   - failedAt:       Error Stream으로 이동된 시각 (epoch ms)
	 *
	 * 처리 후 반드시 ACK → Error Stream의 PEL에서 제거
	 * ACK 없이 종료되면 다음 재시작 시 다시 수신된다 (at-least-once)
	 */
	private void handle(MapRecord<String, String, String> record) {
		Map<String, String> body = record.getValue();

		log.error(
			"[ERROR_STREAM] 기술적 장애로 처리 실패 email={}, slotId={}, date={}, originalStream={}, deliveryCount={}, failedAt={}",
			body.get("userEmail"),
			body.get("slotId"),
			body.get("date"),
			body.get("originalStream"),
			body.get("deliveryCount"),
			body.get("failedAt")
		);

		// ACK → Error Stream PEL에서 제거 (로그 기록 완료)
		redisTemplate.opsForStream().acknowledge(ERROR_STREAM_KEY, ERROR_CONSUMER_GROUP, record.getId());
	}
}
