package com.reservation.tablereservationservice.application.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

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

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

	private final ReservationRepository reservationRepository;
	private final RestaurantSlotRepository restaurantSlotRepository;
	private final PaymentClient paymentClient;
	private final PaymentQueuePublisher paymentQueuePublisher;

	public void confirmPayment(Long userId, PaymentConfirmRequestDto requestDto) {
		Reservation reservation = reservationRepository.fetchByIdempotencyKey(requestDto.getOrderId());

		validateOwner(reservation, userId);

		if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
			return;
		}

		validatePending(reservation);

		RestaurantSlot slot = restaurantSlotRepository.fetchById(reservation.getSlotId());
		validateAmount(requestDto.getAmount(), slot, reservation);

		reservation.recordPaymentKey(requestDto.getPaymentKey());
		reservationRepository.updatePaymentKey(reservation);

		PaymentResult result = requestApproval(PaymentRequest.of(requestDto.getPaymentKey(), requestDto.getOrderId(), requestDto.getAmount()));
		paymentQueuePublisher.publish(PaymentQueueMessage.ofConfirmed(reservation, requestDto.getOrderId(), result));
	}

	private PaymentResult requestApproval(PaymentRequest request) {
		try {
			return paymentClient.confirm(request);
		} catch (CallNotPermittedException e) {
			log.warn("[CIRCUIT_BREAKER] 서킷 오픈 — 결제 차단 orderId={}", request.getOrderId());
			throw new PaymentException(ErrorCode.TOSS_API_UNAVAILABLE);
		} catch (HttpServerErrorException | ResourceAccessException e) {
			log.error("[TOSS] 결제 승인 실패 orderId={}", request.getOrderId(), e);
			throw new PaymentException(ErrorCode.TOSS_API_UNAVAILABLE);
		}
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
