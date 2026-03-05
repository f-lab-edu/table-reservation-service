package com.reservation.tablereservationservice.infrastructure.stream;

import static com.reservation.tablereservationservice.global.config.RedisStreamConfig.*;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.global.util.ProcessingOrderGenerator;
import com.reservation.tablereservationservice.infrastructure.mq.ReservationRequestMessage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(2) // Redis 잔여 좌석 초기화(Order=1) 완료 후 구독을 시작해야 한다.
public class ReservationStreamConsumer implements ApplicationRunner {

	private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
	private final ReservationService reservationService;
	private final ProcessingOrderGenerator orderGenerator;
	private final StringRedisTemplate redisTemplate;
	private final MeterRegistry meterRegistry;

	private Counter successCounter;
	private Counter rejectedCounter;
	private Counter errorCounter;
	private Timer consumerTimer;

	@PostConstruct
	void initMetrics() {
		successCounter = Counter.builder("reservation_consumer_total")
			.tag("result", "success")
			.description("Consumer가 성공적으로 처리한 예약 수")
			.register(meterRegistry);

		rejectedCounter = Counter.builder("reservation_consumer_total")
			.tag("result", "rejected")
			.description("비즈니스 오류로 거절된 메시지 수")
			.register(meterRegistry);

		errorCounter = Counter.builder("reservation_consumer_total")
			.tag("result", "error")
			.description("기술적 오류로 PEL에 남은 메시지 수")
			.register(meterRegistry);

		consumerTimer = Timer.builder("reservation_consumer_duration_seconds")
			.description("Consumer 메시지 1건 처리 시간")
			.register(meterRegistry);
	}

	/**
	 * 앱 시작 시 4개 파티션 스트림 구독 등록 후 컨테이너 시작
	 * ReadOffset.lastConsumed(): Consumer Group이 마지막으로 ACK 한 메시지 ID 이후부터 읽는다.
	 *   재시작 전 ACK 못한 메시지(PEL)는 ReservationStreamErrorHandler가 처리한다.
	 */
	@Override
	public void run(ApplicationArguments args) {
		for (int i = 1; i <= PARTITION_COUNT; i++) {
			String streamKey = STREAM_KEY_PREFIX + i;
			String consumerName = CONSUMER_PREFIX + i;

			listenerContainer.receive(
				Consumer.from(CONSUMER_GROUP, consumerName),
				StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
				this::process
			);
		}

		// 구독 등록 완료 후 폴링 시작
		listenerContainer.start();
		log.info("[StreamConsumer] 4개 파티션 스트림 구독 시작");
	}

	private void process(MapRecord<String, String, String> record) {
		final long processingOrder = orderGenerator.next();
		final Map<String, String> body = record.getValue();
		final String email = body.get("userEmail");
		final Long slotId = Long.parseLong(body.get("slotId"));

		log.info("[STREAM_CONSUMER] consume seq={}, email={}, slotId={}", processingOrder, email, slotId);

		consumerTimer.record(() -> {
			try {
				ReservationRequestMessage message = deserialize(body);
				reservationService.handleReservationRequest(message, processingOrder);

				// 처리 성공 → XACK 전송 → PEL 에서 제거
				ack(record);
				successCounter.increment();
				log.info("[STREAM_CONSUMER] success seq={}, email={}, slotId={}", processingOrder, email, slotId);

			} catch (ReservationException e) {
				// 비즈니스 실패 (좌석 부족, 중복 예약 등)
				// 재시도해도 결과가 같으므로 ACK 로 PEL에서 제거
				// 멱등성: DB 의 (userId, visitAt) 유니크 제약이 중복 INSERT를 막는다.
				ack(record);
				rejectedCounter.increment();
				log.warn("[STREAM_CONSUMER] rejected email={}, slotId={}, reason={}", email, slotId, e.getMessage());

			} catch (Exception e) {
				// 기술적 오류 (DB 타임아웃, 커넥션 풀 고갈 등)
				// ACK 하지 않음 → 메시지가 PEL에 남는다.
				// ReservationStreamErrorHandler가 30초 후 XCLAIM 으로 재처리 시도
				errorCounter.increment();
				log.error(
					"[STREAM_CONSUMER] error email={}, slotId={}, messageId={}",
					email, slotId, record.getId(), e
				);
			}
		});
	}

	/**
	 * XACK: 메시지 처리 완료를 Redis에 알린다.
	 * ACK를 받은 Redis는 해당 메시지를 PEL에서 제거한다.
	 */
	private void ack(MapRecord<String, String, String> record) {
		redisTemplate.opsForStream().acknowledge(record.getStream(), CONSUMER_GROUP, record.getId());
	}

	private ReservationRequestMessage deserialize(Map<String, String> body) {
		return new ReservationRequestMessage(
			body.get("userEmail"),
			Long.parseLong(body.get("slotId")),
			LocalDate.parse(body.get("date")),
			Integer.parseInt(body.get("partySize")),
			body.get("note")
		);
	}
}
