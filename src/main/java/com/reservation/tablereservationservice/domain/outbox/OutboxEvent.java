package com.reservation.tablereservationservice.domain.outbox;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
public class OutboxEvent {

	private Long id;
	private Long aggregateId;
	private String eventType;
	private String payload;
	private OutboxStatus status;
	private LocalDateTime createdAt;
	private LocalDateTime publishedAt;

	@Builder
	public OutboxEvent(
			Long id,
			Long aggregateId,
			String eventType,
			String payload,
			OutboxStatus status,
			LocalDateTime createdAt,
			LocalDateTime publishedAt
	) {
		this.id = id;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = status;
		this.createdAt = createdAt;
		this.publishedAt = publishedAt;
	}

	public static OutboxEvent pending(Long aggregateId, String eventType, String payload) {
		return OutboxEvent.builder()
				.aggregateId(aggregateId)
				.eventType(eventType)
				.payload(payload)
				.status(OutboxStatus.PENDING)
				.build();
	}
}
