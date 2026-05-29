package com.reservation.tablereservationservice.application.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.application.notification.NotificationService;
import com.reservation.tablereservationservice.domain.payment.Payment;
import com.reservation.tablereservationservice.domain.payment.PaymentRepository;
import com.reservation.tablereservationservice.domain.payment.PaymentStatus;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.global.transaction.TransactionHandler;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.PaymentQueueMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmationService {

	private final ReservationRepository reservationRepository;
	private final PaymentRepository paymentRepository;
	private final RestaurantSlotRepository restaurantSlotRepository;
	private final RestaurantRepository restaurantRepository;
	private final NotificationService notificationService;
	private final TransactionHandler transactionHandler;

	@Transactional
	public void confirm(PaymentQueueMessage message) {
		int affected = reservationRepository.updateStatusConditional(
				message.getReservationId(), ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
		if (affected == 0) {
			log.warn("[PAYMENT] 이미 처리된 예약 — 중복 메시지 reservationId={}", message.getReservationId());
			return;
		}

		Reservation reservation = reservationRepository.fetchById(message.getReservationId());

		paymentRepository.save(Payment.builder()
				.reservationId(message.getReservationId())
				.idempotencyKey(message.getIdempotencyKey())
				.paymentKey(message.getPaymentKey())
				.amount(message.getAmount())
				.status(PaymentStatus.DONE)
				.approvedAt(message.getApprovedAt())
				.build());

		Restaurant restaurant = findRestaurantBySlotId(reservation.getSlotId());

		// TODO: 알림 발송 타이밍 검토
		transactionHandler.runAfterCommit(() ->
				notificationService.notifyConfirmed(reservation, restaurant.getOwnerId(), restaurant.getName()));
	}

	private Restaurant findRestaurantBySlotId(Long slotId) {
		RestaurantSlot slot = restaurantSlotRepository.fetchById(slotId);
		return restaurantRepository.findById(slot.getRestaurantId())
				.orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Restaurant"));
	}
}
