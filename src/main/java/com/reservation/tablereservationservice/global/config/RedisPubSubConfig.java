package com.reservation.tablereservationservice.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.reservation.tablereservationservice.infrastructure.notification.pubsub.NotificationSubscriber;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

	@Value("${redis.pubsub.notification-channel}")
	private String notificationChannel;

	private final NotificationSubscriber notificationSubscriber;

	@Bean
	public RedisMessageListenerContainer notificationListenerContainer(RedisConnectionFactory connectionFactory) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.addMessageListener(notificationSubscriber, new ChannelTopic(notificationChannel));
		return container;
	}
}