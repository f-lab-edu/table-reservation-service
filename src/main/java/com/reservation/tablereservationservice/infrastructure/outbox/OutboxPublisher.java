package com.reservation.tablereservationservice.infrastructure.outbox;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.tablereservationservice.domain.outbox.OutboxEvent;
import com.reservation.tablereservationservice.domain.outbox.OutboxRepository;
import com.reservation.tablereservationservice.global.config.OutboxProperties;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.CancelQueueMessage;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.CancelQueuePublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

	private final OutboxRepository outboxRepository;
	private final CancelQueuePublisher cancelQueuePublisher;
	private final OutboxProperties outboxProperties;
	private final ObjectMapper objectMapper;

	/**
	 * 읽기(FOR UPDATE SKIP LOCKED) → 발행 → 보냄 표시를 한 트랜잭션으로 묶는다.
	 * 잠금이 발행까지 유지돼 여러 인스턴스가 같은 행을 중복 발행하지 않는다.
	 * READ COMMITTED로 낮춰 FOR UPDATE의 gap lock이 새 취소 INSERT를 막지 않게 한다.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void publishPending() {
		List<OutboxEvent> pending = outboxRepository.findPendingForUpdate(outboxProperties.getBatchSize());
		if (pending.isEmpty()) {
			return;
		}

		List<Long> publishedIds = new ArrayList<>(pending.size());
		for (OutboxEvent event : pending) {
			try {
				publish(event);
				publishedIds.add(event.getId());
			} catch (Exception e) {
				log.warn("[OUTBOX] 발행 실패 — 다음 폴링에 재시도 outboxId={} aggregateId={}", event.getId(), event.getAggregateId(), e);
			}
		}

		if (!publishedIds.isEmpty()) {
			outboxRepository.markPublished(publishedIds, LocalDateTime.now());
			log.info("[OUTBOX] 발행 완료 count={}", publishedIds.size());
		}
	}

	private void publish(OutboxEvent event) throws Exception {
		CancelQueueMessage message = objectMapper.readValue(event.getPayload(), CancelQueueMessage.class);
		cancelQueuePublisher.publish(message);
	}

	public void cleanupPublished() {
		LocalDateTime threshold = LocalDateTime.now().minusDays(outboxProperties.getRetentionDays());
		int deleted = outboxRepository.deletePublishedBefore(threshold);
		if (deleted > 0) {
			log.info("[OUTBOX] 오래된 PUBLISHED 정리 count={}", deleted);
		}
	}
}
