package com.reservation.tablereservationservice.application.notification;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.reservation.tablereservationservice.infrastructure.notification.sse.SseEmitterRegistry;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final SseEmitterRegistry sseEmitterRegistry;

	public SseEmitter subscribe(Long receiverId) {
		SseEmitter emitter = sseEmitterRegistry.register(receiverId);

		// 구독 확인용 데이터 전송
		sseEmitterRegistry.send(receiverId, "connected", "ok");

		return emitter;
	}
}
