package com.reservation.tablereservationservice.infrastructure.notification.repository;

import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.notification.Notification;
import com.reservation.tablereservationservice.domain.notification.NotificationRepository;
import com.reservation.tablereservationservice.infrastructure.notification.entity.NotificationEntity;
import com.reservation.tablereservationservice.infrastructure.notification.mapper.NotificationMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaNotificationRepository implements NotificationRepository {

	private final NotificationEntityRepository notificationEntityRepository;

	@Override
	public Notification save(Notification notification) {
		NotificationEntity entity = NotificationMapper.INSTANCE.toEntity(notification);
		NotificationEntity saved = notificationEntityRepository.save(entity);

		return NotificationMapper.INSTANCE.toDomain(saved);
	}
}
