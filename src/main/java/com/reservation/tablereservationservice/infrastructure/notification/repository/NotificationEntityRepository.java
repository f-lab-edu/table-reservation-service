package com.reservation.tablereservationservice.infrastructure.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reservation.tablereservationservice.infrastructure.notification.entity.NotificationEntity;

public interface NotificationEntityRepository extends JpaRepository<NotificationEntity, Long> {

	Page<NotificationEntity> findAllByReceiverIdOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

	@Modifying
	@Query("UPDATE NotificationEntity n SET n.isRead = true WHERE n.receiverId = :receiverId AND n.isRead = false")
	void markAllAsRead(@Param("receiverId") Long receiverId);
}
