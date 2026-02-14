package com.reservation.tablereservationservice.infrastructure.reservation.entity;

import java.time.LocalDateTime;

import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.infrastructure.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
	name = "reservation",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_user_visit_at",
			columnNames = {"user_id", "visit_at"}
		)
	}
)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)

public class ReservationEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long reservationId;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long slotId;

	@Column(nullable = false)
	private LocalDateTime visitAt;

	@Column(nullable = false)
	private Integer partySize;

	private String note;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReservationStatus status;

	@Column(nullable = false)
	private String requestId;

	@Column(nullable = false)
	private long serverReceivedSeq;

	@Builder
	public ReservationEntity(Long userId, Long slotId, LocalDateTime visitAt, Integer partySize,
		String note, ReservationStatus status, String requestId, long serverReceivedSeq) {
		this.userId = userId;
		this.slotId = slotId;
		this.visitAt = visitAt;
		this.partySize = partySize;
		this.note = note;
		this.status = status;
		this.requestId = requestId;
		this.serverReceivedSeq = serverReceivedSeq;
	}

	public void updateStatus(ReservationStatus status) {
	    this.status = status;
	}
}
