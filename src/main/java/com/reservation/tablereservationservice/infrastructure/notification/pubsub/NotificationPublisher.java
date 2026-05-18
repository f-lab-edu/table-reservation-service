package com.reservation.tablereservationservice.infrastructure.notification.pubsub;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.tablereservationservice.domain.notification.AlarmMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

	@Value("${redis.pubsub.notification-channel}")
	private String channel;

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public void publish(AlarmMessage message) {
		try {
			NotificationEvent event = new NotificationEvent(
				message.getReceiverId(),
				message.getReservationId(),
				message.getType(),
				message.getTitle(),
				message.getContent()
			);
			String json = objectMapper.writeValueAsString(event);
			redisTemplate.convertAndSend(channel, json);
			log.debug("[PubSub] 알림 발행 receiverId={}, type={}", message.getReceiverId(), message.getType());
		} catch (JsonProcessingException e) {
			log.error("[PubSub] 알림 직렬화 실패 receiverId={}", message.getReceiverId(), e);
		}
	}
}