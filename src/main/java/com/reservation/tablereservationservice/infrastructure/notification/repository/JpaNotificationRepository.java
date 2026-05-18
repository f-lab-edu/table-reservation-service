package com.reservation.tablereservationservice.infrastructure.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

	@Override
	public Page<Notification> findAllByReceiverId(Long receiverId, Pageable pageable) {
		return notificationEntityRepository.findAllByReceiverIdOrderByCreatedAtDesc(receiverId, pageable)
				.map(NotificationMapper.INSTANCE::toDomain);
	}

	@Override
	@Transactional
	public void markAllAsRead(Long receiverId) {
		notificationEntityRepository.markAllAsRead(receiverId);
	}
}
