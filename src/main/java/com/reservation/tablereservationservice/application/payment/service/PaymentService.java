package com.reservation.tablereservationservice.application.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.application.payment.PaymentClient;
import com.reservation.tablereservationservice.application.payment.PaymentRequest;
import com.reservation.tablereservationservice.application.payment.PaymentResult;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.PaymentException;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.PaymentQueueMessage;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.PaymentQueuePublisher;
import com.reservation.tablereservationservice.presentation.payment.dto.PaymentConfirmRequestDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final ReservationRepository reservationRepository;
	private final RestaurantSlotRepository restaurantSlotRepository;
	private final PaymentClient paymentClient;
	private final PaymentQueuePublisher paymentQueuePublisher;

	@Transactional(readOnly = true)
	public void confirmPayment(Long userId, PaymentConfirmRequestDto requestDto) {
		Reservation reservation = reservationRepository.fetchByIdempotencyKey(requestDto.getOrderId());

		validateOwner(reservation, userId);

		if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
			return;
		}

		validatePending(reservation);

		RestaurantSlot slot = restaurantSlotRepository.fetchById(reservation.getSlotId());
		validateAmount(requestDto.getAmount(), slot, reservation);

		PaymentResult result = paymentClient.confirm(PaymentRequest.from(requestDto));
		paymentQueuePublisher.publish(PaymentQueueMessage.of(reservation, requestDto.getOrderId(), result));
	}

	private void validateOwner(Reservation reservation, Long userId) {
		if (!reservation.isOwner(userId)) {
			throw new PaymentException(ErrorCode.ACCESS_DENIED);
		}
	}

	private void validatePending(Reservation reservation) {
		if (reservation.getStatus() != ReservationStatus.PENDING) {
			throw new PaymentException(ErrorCode.PAYMENT_RESERVATION_FAILED);
		}
	}

	private void validateAmount(Integer requestedAmount, RestaurantSlot slot, Reservation reservation) {
		int expectedAmount = slot.depositAmount(reservation.getPartySize());
		if (!requestedAmount.equals(expectedAmount)) {
			throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
		}
	}
}
