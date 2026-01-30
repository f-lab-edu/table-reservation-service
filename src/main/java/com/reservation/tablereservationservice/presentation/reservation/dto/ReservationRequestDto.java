package com.reservation.tablereservationservice.presentation.reservation.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReservationRequestDto {

	@NotNull(message = "slotId는 필수입니다.")
	private Long slotId;

	@NotNull(message = "예약 날짜는 필수입니다.")
	private LocalDate date;

	@Min(value = 1, message = "예약 인원은 1명 이상이어야 합니다.")
	private int partySize;

	@Size(max = 200, message = "요청 사항은 최대 200자까지 가능합니다.")
	private String note;
}
