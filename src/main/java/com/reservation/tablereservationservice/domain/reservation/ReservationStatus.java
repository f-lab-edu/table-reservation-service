package com.reservation.tablereservationservice.domain.reservation;

import lombok.Getter;

@Getter
public enum ReservationStatus {

	PENDING,
	CONFIRMED,
	CANCELED
}
