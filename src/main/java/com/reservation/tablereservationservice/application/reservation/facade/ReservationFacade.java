package com.reservation.tablereservationservice.application.reservation.facade;

import com.reservation.tablereservationservice.application.reservation.service.ReservationCreateResult;
import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.global.annotation.Idempotent;
import com.reservation.tablereservationservice.infrastructure.redis.ReservationPublisher;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationFacade {

	private final ReservationService reservationService;
	private final ReservationPublisher reservationPublisher;

	@Idempotent
	public ReservationCreateResult reserve(String email, ReservationRequestDto requestDto, String idempotencyKey) {
		reservationPublisher.holdSeat(requestDto.getSlotId(), requestDto.getDate(), requestDto.getPartySize());
		try {
			return reservationService.reserve(email, requestDto, idempotencyKey);
		} catch (Exception e) {
			reservationPublisher.releaseSeat(requestDto.getSlotId(), requestDto.getDate(), requestDto.getPartySize());
			throw e;
		}
	}
}
