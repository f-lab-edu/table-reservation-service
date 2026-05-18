package com.reservation.tablereservationservice.application.notification;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.reservation.tablereservationservice.domain.notification.AlarmMessage;
import com.reservation.tablereservationservice.domain.notification.Notification;
import com.reservation.tablereservationservice.domain.notification.NotificationRepository;
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

	@Transactional
	public void notifyReservationConfirmed(Long customerId, Long ownerId, Long reservationId, LocalDateTime visitAt, int partySize) {
		stage(AlarmMessage.reservationConfirmed(customerId, reservationId, visitAt, partySize));
		stage(AlarmMessage.ownerReservationConfirmed(ownerId, reservationId, visitAt, partySize));
	}

	@Transactional
	public void notifyReservationCancelled(Long customerId, Long ownerId, Long reservationId, LocalDateTime visitAt, int partySize) {
		stage(AlarmMessage.reservationCancelled(customerId, reservationId, visitAt, partySize));
		stage(AlarmMessage.ownerReservationCancelled(ownerId, reservationId, visitAt, partySize));
	}

	private void stage(AlarmMessage message) {
		notificationRepository.save(Notification.from(message));
		transactionHandler.runAfterCommit(() -> pubSubPublisher.publish(message));
	}

	@Transactional(readOnly = true)
	public PageResponseDto<NotificationResponseDto> findAll(Long receiverId, Pageable pageable) {
		Page<Notification> page = notificationRepository.findAllByReceiverId(receiverId, pageable);
		return PageResponseDto.from(page.map(NotificationResponseDto::from));
	}

	@Transactional
	public void markAsRead(Long notificationId, Long receiverId) {
		notificationRepository.markAsRead(notificationId, receiverId);
	}
}
