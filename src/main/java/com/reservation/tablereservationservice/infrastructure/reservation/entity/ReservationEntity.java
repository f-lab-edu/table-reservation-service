package com.reservation.tablereservationservice.infrastructure.reservation.entity;

import java.time.LocalDateTime;

import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.infrastructure.common.entity.BaseTimeEntity;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantSlotEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
			columnNames = {"reservation_user_id", "reservation_visit_at"}
		)
	}
)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)

public class ReservationEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long reservationId;

	@Column(name = "reservation_user_id", nullable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reservation_slot_id", nullable = false)
	private RestaurantSlotEntity slot;

	@Column(name = "reservation_visit_at", nullable = false)
	private LocalDateTime visitAt;

	@Column(name = "reservation_party_size", nullable = false)
	private Integer partySize;

	@Column(name = "reservation_note")
	private String note;

	@Enumerated(EnumType.STRING)
	@Column(name = "reservation_status", nullable = false, length = 20)
	private ReservationStatus status;

	@Builder
	public ReservationEntity(Long userId, RestaurantSlotEntity slot, LocalDateTime visitAt, Integer partySize,
		String note, ReservationStatus status) {
		this.userId = userId;
		this.slot = slot;
		this.visitAt = visitAt;
		this.partySize = partySize;
		this.note = note;
		this.status = status;
	}

	public void assignSlot(RestaurantSlotEntity slot) {
		this.slot = slot;
	}
}
