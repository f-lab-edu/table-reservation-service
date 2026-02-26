package com.reservation.tablereservationservice.global.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RabbitMqConfig {

	public static final String RESERVATION_EXCHANGE = "reservation.exchange";
	public static final String RESERVATION_REQUEST_QUEUE = "reservation.request.queue";
	public static final String RESERVATION_REQUEST_ROUTING_KEY = "reservation.request";

	@Bean
	public DirectExchange reservationExchange() {
		return new DirectExchange(RESERVATION_EXCHANGE);
	}

	@Bean
	public Queue reservationRequestQueue() {
		return new Queue(RESERVATION_REQUEST_QUEUE, true);
	}

	@Bean
	public Binding reservationRequestBinding(Queue reservationRequestQueue, DirectExchange reservationExchange) {
		return BindingBuilder.bind(reservationRequestQueue)
			.to(reservationExchange)
			.with(RESERVATION_REQUEST_ROUTING_KEY);
	}

	@Bean
	public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
		ObjectMapper copy = objectMapper.copy();
		copy.findAndRegisterModules();
		return new Jackson2JsonMessageConverter(copy);
	}

	@Bean
	public RabbitTemplate rabbitTemplate(
		ConnectionFactory connectionFactory,
		Jackson2JsonMessageConverter jackson2JsonMessageConverter
	) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMessageConverter(jackson2JsonMessageConverter);
		return template;
	}

	@Bean
	public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
		SimpleRabbitListenerContainerFactoryConfigurer configurer,
		ConnectionFactory connectionFactory,
		Jackson2JsonMessageConverter jackson2JsonMessageConverter
	) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		configurer.configure(factory, connectionFactory);
		factory.setMessageConverter(jackson2JsonMessageConverter);
		factory.setConcurrentConsumers(1);
		factory.setMaxConcurrentConsumers(1);
		factory.setPrefetchCount(1);
		factory.setDefaultRequeueRejected(false);

		return factory;
	}
}
