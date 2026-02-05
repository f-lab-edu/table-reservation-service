package com.reservation.tablereservationservice.infrastructure.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reservation.tablereservationservice.infrastructure.notification.entity.NotificationEntity;

public interface NotificationEntityRepository extends JpaRepository<NotificationEntity, Long> {
}
