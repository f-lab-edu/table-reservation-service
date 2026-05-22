package com.reservation.tablereservationservice.application.reservation.service;

public record ReservationIdempotencyCache(Long reservationId, int depositAmount) {
}
