package com.reservation.tablereservationservice.global.common;

public record CurrentUser(
    Long userId,
    String email
) {}
