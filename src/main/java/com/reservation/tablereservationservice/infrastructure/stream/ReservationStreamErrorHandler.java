package com.reservation.tablereservationservice.infrastructure.stream;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.global.util.ProcessingOrderGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationStreamErrorHandler {

	// PEL(Pending Entry List): Consumer가 XREADGROUP으로 가져갔지만 아직 ACK 하지 않은 메시지 목록
	// Redis가 이 목록을 유지하기 때문에 Consumer가 죽어도 메시지가 유실되지 않는다.
	// IDLE_THRESHOLD: PEL에서 이 시간 이상 idle(마지막 배달 이후 경과 시간) 한 메시지를 재처리 대상으로 간주
	// Consumer가 메시지를 받고 처리 중 죽거나 기술적 오류로 ACK를 못 보낸 경우를 탐지
	private static final Duration IDLE_THRESHOLD = Duration.ofSeconds(30);

	// MAX_RETRY: 재처리 시도 횟수 상한. 초과 시 Error Stream(DLQ)으로 이동
	private static final int MAX_RETRY = 3;
	private static final int PENDING_BATCH = 10;

	private final StringRedisTemplate redisTemplate;
	private final ReservationService reservationService;
	private final ProcessingOrderGenerator orderGenerator;
	private final ReservationStreamProperties streamProperties;

	/**
	 * 1분마다 실행되는 PEL 감시 스케줄러
	 *
	 * 흐름:
	 *   1. 각 파티션 스트림의 PEL을 XPENDING으로 조회
	 *   2. IDLE_THRESHOLD(30초) 이상 idle 한 메시지 선별
	 *   3. deliveryCount ≤ MAX_RETRY → XCLAIM 후 재처리 시도
	 *      deliveryCount >  MAX_RETRY → Error Stream(DLQ) 으로 이동
	 */
	@Scheduled(fixedDelay = 60_000)
	public void reclaimStaleMessages() {
		for (int i = 1; i <= streamProperties.getPartitionCount(); i++) {
			String streamKey = streamProperties.getKeyPrefix() + i;
			String consumerName = streamProperties.getConsumerPrefix() + i;
			try {
				processStaleMessages(streamKey, consumerName);
			} catch (Exception e) {
				log.error("[XCLAIM] PEL 처리 중 오류 stream={}", streamKey, e);
			}
		}
	}

	/**
	 * 단일 파티션 스트림의 PEL을 조회하고 stale 메시지를 처리한다.
	 *
	 * XPENDING stream group - + COUNT:
	 *   "-" = 가장 오래된 ID, "+" = 가장 최신 ID (전체 범위)
	 *   → PEL 에 있는 메시지를 오래된 순으로 최대 PENDING_BATCH 건 조회
	 */
	private void processStaleMessages(String streamKey, String consumerName) {
		PendingMessages pending = redisTemplate.opsForStream()
			.pending(streamKey, streamProperties.getConsumerGroup(), Range.unbounded(), PENDING_BATCH);

		if (pending.isEmpty())
			return;

		for (PendingMessage msg : pending) {
			// IDLE_THRESHOLD 미만 → 아직 정상 처리 중일 수 있음. 건너뜀
			if (msg.getElapsedTimeSinceLastDelivery().compareTo(IDLE_THRESHOLD) < 0)
				continue;

			RecordId recordId = msg.getId();
			long deliveryCount = msg.getTotalDeliveryCount();

			if (deliveryCount > MAX_RETRY) {
				// 재시도 상한 초과 → 사람이 개입해야 하는 장애. Error Stream으로 격리
				moveToErrorStream(streamKey, consumerName, recordId, deliveryCount);
			} else {
				// 재시도 가능 → XCLAIM으로 소유권을 이 스케줄러로 옮기고 재처리
				reclaimAndReprocess(streamKey, consumerName, recordId, deliveryCount);
			}
		}
	}

	/**
	 * XCLAIM 으로 메시지 소유권을 가져온 뒤 재처리한다.
	 *
	 * XCLAIM stream group consumer min-idle-time id:
	 *   - 지정한 id 의 소유권을 consumerName으로 변경
	 *   - 메시지의 idle 시간을 0 으로 초기화 (다음 XPENDING 에서 다시 탐지되지 않도록)
	 *   - deliveryCount를 1 증가
	 *   - 반환값: 소유권이 변경된 메시지의 전체 본문
	 *
	 * 처리 결과:
	 *   성공/비즈니스 실패 → ACK → PEL에서 제거
	 *   기술적 오류 → ACK 안 함 → PEL에 남아 다음 주기에 재시도
	 */
	@SuppressWarnings("unchecked")
	private void reclaimAndReprocess(String streamKey, String consumerName, RecordId recordId, long deliveryCount) {
		List<MapRecord<String, String, String>> records = (List<MapRecord<String, String, String>>)(List<?>)
			redisTemplate.opsForStream()
				.claim(streamKey, streamProperties.getConsumerGroup(), consumerName, IDLE_THRESHOLD, recordId);

		if (records.isEmpty())
			return;

		MapRecord<String, String, String> record = records.get(0);
		Map<String, String> body = record.getValue();
		String email = body.get("userEmail");
		Long slotId = Long.parseLong(body.get("slotId"));

		log.warn("[XCLAIM] 재처리 시도 email={}, slotId={}, deliveryCount={}", email, slotId, deliveryCount);

		try {
			ReservationRequestMessage message = ReservationRequestMessage.fromMap(body);
			reservationService.handleReservationRequest(message, orderGenerator.next());

			ack(streamKey, recordId);
			log.info("[XCLAIM] 재처리 성공 email={}, slotId={}", email, slotId);

		} catch (ReservationException e) {
			ack(streamKey, recordId);
			log.warn("[XCLAIM] 재처리 비즈니스 거절 email={}, slotId={}, reason={}", email, slotId, e.getMessage());

		} catch (Exception e) {
			log.error("[XCLAIM] 재처리 실패 email={}, slotId={} → 다음 주기에 재시도", email, slotId, e);
		}
	}

	/**
	 * MAX_RETRY 초과 메시지를 Error Stream으로 이동하고 원본 파티션 스트림에서 ACK
	 * Error Stream은 RabbitMQ의 DLQ와 동일한 역할을 한다.
	 *
	 * 이동 방식:
	 *   1. XCLAIM 으로 소유권 획득 + 원본 메시지 본문 조회
	 *   2. 원본 필드 + 메타데이터(originalStream, originalId, deliveryCount, failedAt) 를 Error Stream 에 XADD
	 *   3. 원본 파티션 스트림에서 XACK → PEL에서 제거
	 */
	@SuppressWarnings("unchecked")
	private void moveToErrorStream(String streamKey, String consumerName, RecordId recordId, long deliveryCount) {
		List<MapRecord<String, String, String>> records = (List<MapRecord<String, String, String>>)(List<?>)
			redisTemplate.opsForStream()
				.claim(streamKey, streamProperties.getConsumerGroup(), consumerName, IDLE_THRESHOLD, recordId);

		if (records.isEmpty())
			return;

		Map<String, String> body = records.get(0).getValue();
		String email = body.get("userEmail");
		Long slotId = Long.parseLong(body.get("slotId"));

		log.error(
			"[XCLAIM] MAX_RETRY 초과 → Error Stream 이동 email={}, slotId={}, deliveryCount={}",
			email,
			slotId,
			deliveryCount
		);

		// Error Stream에 원본 메시지 + 메타데이터 저장
		ErrorStreamEntry errorEntry = ErrorStreamEntry.of(body, streamKey, recordId.getValue(), deliveryCount);
		redisTemplate.opsForStream().add(streamProperties.getErrorStreamKey(), errorEntry.toMap());

		ack(streamKey, recordId);
	}

	private void ack(String streamKey, RecordId recordId) {
		redisTemplate.opsForStream().acknowledge(streamKey, streamProperties.getConsumerGroup(), recordId);
	}

}
