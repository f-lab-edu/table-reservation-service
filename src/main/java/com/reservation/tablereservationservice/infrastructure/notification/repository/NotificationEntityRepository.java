package com.reservation.tablereservationservice.infrastructure.notification.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.reservation.tablereservationservice.infrastructure.notification.entity.NotificationEntity;

public interface NotificationEntityRepository extends JpaRepository<NotificationEntity, Long> {

	Page<NotificationEntity> findAllByReceiverIdOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

	Optional<NotificationEntity> findByNotificationIdAndReceiverId(Long notificationId, Long receiverId);
}
