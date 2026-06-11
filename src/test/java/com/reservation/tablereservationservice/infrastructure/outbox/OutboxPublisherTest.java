package com.reservation.tablereservationservice.infrastructure.outbox;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.tablereservationservice.domain.outbox.OutboxEvent;
import com.reservation.tablereservationservice.domain.outbox.OutboxRepository;
import com.reservation.tablereservationservice.global.config.OutboxProperties;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.CancelQueueMessage;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.CancelQueuePublisher;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

	@Mock
	private OutboxRepository outboxRepository;
	@Mock
	private CancelQueuePublisher cancelQueuePublisher;
	@Mock
	private OutboxProperties outboxProperties;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private OutboxPublisher outboxPublisher;

	@BeforeEach
	void setUp() {
		outboxPublisher = new OutboxPublisher(outboxRepository, cancelQueuePublisher, outboxProperties, objectMapper);
	}

	private OutboxEvent pendingEvent(Long id, Long reservationId) throws Exception {
		CancelQueueMessage message = CancelQueueMessage.of(reservationId);
		String payload = objectMapper.writeValueAsString(message);
		return OutboxEvent.builder()
				.id(id)
				.aggregateId(reservationId)
				.eventType(CancelQueueMessage.OUTBOX_EVENT_TYPE)
				.payload(payload)
				.build();
	}

	@Test
	@DisplayName("PENDING 이벤트를 발행하고 PUBLISHED로 표시한다")
	void publishPending_publishesAndMarks() throws Exception {
		given(outboxProperties.getBatchSize()).willReturn(100);
		given(outboxRepository.findPending(100)).willReturn(List.of(pendingEvent(1L, 999L)));

		outboxPublisher.publishPending();

		verify(cancelQueuePublisher).publish(argThat((CancelQueueMessage m) -> m.getReservationId().equals(999L)));
		verify(outboxRepository).markPublished(eq(List.of(1L)), any(LocalDateTime.class));
	}

	@Test
	@DisplayName("한 건 발행 실패는 격리되어 나머지는 발행되고, 실패 건은 PUBLISHED 미표시")
	void publishPending_isolatesFailure() throws Exception {
		given(outboxProperties.getBatchSize()).willReturn(100);
		given(outboxRepository.findPending(100)).willReturn(List.of(pendingEvent(1L, 100L), pendingEvent(2L, 200L)));
		willThrow(new RuntimeException("broker down"))
				.given(cancelQueuePublisher).publish(argThat((CancelQueueMessage m) -> m.getReservationId().equals(100L)));

		outboxPublisher.publishPending();

		verify(outboxRepository).markPublished(eq(List.of(2L)), any(LocalDateTime.class));
	}

	@Test
	@DisplayName("PENDING이 없으면 아무것도 하지 않는다")
	void publishPending_empty_noop() {
		given(outboxProperties.getBatchSize()).willReturn(100);
		given(outboxRepository.findPending(100)).willReturn(List.of());

		outboxPublisher.publishPending();

		verifyNoInteractions(cancelQueuePublisher);
		verify(outboxRepository, never()).markPublished(any(), any());
	}

	@Test
	@DisplayName("정리 잡은 보관 기간이 지난 PUBLISHED를 삭제한다")
	void cleanupPublished_deletesOld() {
		given(outboxProperties.getRetentionDays()).willReturn(7);
		given(outboxRepository.deletePublishedBefore(any(LocalDateTime.class))).willReturn(3);

		outboxPublisher.cleanupPublished();

		verify(outboxRepository).deletePublishedBefore(any(LocalDateTime.class));
	}
}
