package com.reservation.tablereservationservice.application.reservation.service;

import com.reservation.tablereservationservice.domain.reservation.Reservation;

public record ReservationCreateResult(Reservation reservation, int depositAmount) {
}
