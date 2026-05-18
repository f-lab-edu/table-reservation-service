package com.reservation.tablereservationservice.domain.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationRepository {

	Notification save(Notification notification);

	Page<Notification> findAllByReceiverId(Long receiverId, Pageable pageable);

	void markAsRead(Long notificationId, Long receiverId);
}
