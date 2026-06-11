package com.reservation.tablereservationservice.infrastructure.outbox.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.reservation.tablereservationservice.domain.outbox.OutboxStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "outbox_event")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OutboxEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long aggregateId;

	@Column(nullable = false, length = 50)
	private String eventType;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OutboxStatus status;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime publishedAt;

	@Builder
	public OutboxEventEntity(
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
}
