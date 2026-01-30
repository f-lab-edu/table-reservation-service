package com.reservation.tablereservationservice.presentation.reservation.dto;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;

import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationSearchDto {
	private LocalDate fromDate;
	private LocalDate toDate;
	private ReservationStatus status;

	private Pageable pageable;

	/**
	 * 조회 시작일 (null 이면 today)
	 */
	public LocalDate getStartDate() {
		return fromDate != null ? fromDate : LocalDate.now();
	}

	/**
	 * 조회 종료일 (null 이면 start + 1개월)
	 */
	public LocalDate getEndDate() {
		LocalDate start = getStartDate();
		return toDate != null ? toDate : start.plusMonths(1);
	}
}
