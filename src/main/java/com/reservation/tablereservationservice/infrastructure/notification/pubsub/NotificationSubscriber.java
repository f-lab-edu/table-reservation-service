package com.reservation.tablereservationservice.infrastructure.notification.pubsub;

import java.io.IOException;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.tablereservationservice.infrastructure.notification.sse.SseEmitterRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSubscriber implements MessageListener {

	private final SseEmitterRegistry sseEmitterRegistry;
	private final ObjectMapper objectMapper;

	/**
	 * Redis Pub/Sub 메시지 수신 시 호출된다.
	 * 모든 서버 인스턴스가 이 메서드를 실행하지만, 실제 SSE 전송은
	 * 해당 receiverId의 emitter를 보유한 서버에서만 이루어진다.
	 */
	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			NotificationEvent event = objectMapper.readValue(message.getBody(), NotificationEvent.class);
			sseEmitterRegistry.send(event.receiverId(), "notification", event);
			log.debug("[PubSub] 알림 수신 receiverId={}, type={}", event.receiverId(), event.type());
		} catch (IOException e) {
			log.error("[PubSub] 알림 역직렬화 실패", e);
		}
	}
}
