package com.reservation.tablereservationservice.application.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.reservation.tablereservationservice.domain.notification.Notification;
import com.reservation.tablereservationservice.domain.notification.NotificationRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.global.transaction.TransactionHandler;
import com.reservation.tablereservationservice.infrastructure.notification.pubsub.NotificationPublisher;
import com.reservation.tablereservationservice.infrastructure.notification.sse.SseEmitterRegistry;
import com.reservation.tablereservationservice.presentation.common.PageResponseDto;
import com.reservation.tablereservationservice.presentation.notification.dto.NotificationResponseDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

	private final SseEmitterRegistry sseEmitterRegistry;
	private final NotificationRepository notificationRepository;
	private final NotificationPublisher pubSubPublisher;
	private final TransactionHandler transactionHandler;

	public SseEmitter subscribe(Long receiverId) {
		SseEmitter emitter = sseEmitterRegistry.register(receiverId);

		// 구독 확인용 데이터 전송
		sseEmitterRegistry.send(receiverId, "connected", "ok");

		return emitter;
	}

	@Async
	@Transactional
	public void notifyConfirmed(Reservation reservation, Long ownerId, String restaurantName) {
		persist(Notification.customerConfirmed(reservation, restaurantName));
		persist(Notification.ownerConfirmed(reservation, ownerId));
	}

	@Async
	@Transactional
	public void notifyCanceled(Reservation reservation, Long ownerId, String restaurantName) {
		persist(Notification.customerCanceled(reservation, restaurantName));
		persist(Notification.ownerCanceled(reservation, ownerId));
	}

	private void persist(Notification notification) {
		notificationRepository.save(notification);
		transactionHandler.runAfterCommit(() -> pubSubPublisher.publish(notification));
	}

	@Transactional
	public PageResponseDto<NotificationResponseDto> findAll(Long receiverId, Pageable pageable) {
		Page<Notification> page = notificationRepository.findAllByReceiverId(receiverId, pageable);
		notificationRepository.markAllAsRead(receiverId);
		return PageResponseDto.from(page.map(NotificationResponseDto::from));
	}
}
