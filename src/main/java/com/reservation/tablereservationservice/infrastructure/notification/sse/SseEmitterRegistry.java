package com.reservation.tablereservationservice.infrastructure.notification.sse;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterRegistry {

	private static final long DEFAULT_TIMEOUT_MS = 60L * 1000 * 60; // 1시간

	private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

	public SseEmitter register(Long receiverId) {
		SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);

		SseEmitter previous = swapEmitter(receiverId, emitter);
		closeExistingConnection(previous);

		emitter.onCompletion(() -> emitters.remove(receiverId, emitter));
		emitter.onTimeout(() -> emitters.remove(receiverId, emitter));
		emitter.onError(ex -> emitters.remove(receiverId, emitter));

		return emitter;
	}

	public void remove(Long receiverId) {
		SseEmitter removed = emitters.remove(receiverId);
		closeExistingConnection(removed);
	}

	public Optional<SseEmitter> get(Long receiverId) {
		return Optional.ofNullable(emitters.get(receiverId));
	}

	public void send(Long receiverId, String eventName, Object data) {
		SseEmitter emitter = emitters.get(receiverId);
		if (emitter == null) {
			return;
		}
		try {
			emitter.send(SseEmitter.event()
				.name(eventName)
				.data(data, MediaType.APPLICATION_JSON));
		} catch (IOException ex) {
			remove(receiverId);
		}
	}

	/**
	 * receiverId에 대한 emitter를 원자적으로 교체하고, 교체되기 전 emitter를 반환한다.
	 */
	private SseEmitter swapEmitter(Long receiverId, SseEmitter newEmitter) {
		return emitters.put(receiverId, newEmitter);
	}

	/**
	 * 기존 SSE 연결을 정상 종료한다.
	 */
	private void closeExistingConnection(SseEmitter existing) {
		if (existing == null) {
			return;
		}
		try {
			existing.complete();
		} catch (Exception ignored) {
			// already completed / client disconnected
		}
	}
}
