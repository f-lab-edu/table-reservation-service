package com.reservation.tablereservationservice.domain.reservation;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
public class Reservation {

	private Long reservationId;
	private Long userId;
	private Long slotId;
	private LocalDateTime visitAt;
	private Integer partySize;
	private String note;
	private ReservationStatus status;
	private String idempotencyKey;
	private String paymentKey;

	@Builder
	public Reservation(
			Long reservationId,
			Long userId,
			Long slotId,
			LocalDateTime visitAt,
			Integer partySize,
			String note,
			ReservationStatus status,
			String idempotencyKey,
			String paymentKey
	) {
		this.reservationId = reservationId;
		this.userId = userId;
		this.slotId = slotId;
		this.visitAt = visitAt;
		this.partySize = partySize;
		this.note = note;
		this.status = status;
		this.idempotencyKey = idempotencyKey;
		this.paymentKey = paymentKey;
	}

	public void recordPaymentKey(String paymentKey) {
		this.paymentKey = paymentKey;
	}

	public boolean isOwner(Long userId) {
		return this.userId != null && this.userId.equals(userId);
	}

	public boolean isAlreadyCanceled() {
		return this.status == ReservationStatus.CANCELED;
	}

	/**
	 * 취소 가능 기한(방문 24시간 전) 내에 있는지 확인
	 * @param now 현재 시점
	 */
	public boolean canCancelAt(LocalDateTime now) {
		LocalDateTime cancelDeadline = this.visitAt.minusHours(24);
		return now.isBefore(cancelDeadline);
	}

	public void markCancelPending() {
		this.status = ReservationStatus.CANCEL_PENDING;
	}
}
