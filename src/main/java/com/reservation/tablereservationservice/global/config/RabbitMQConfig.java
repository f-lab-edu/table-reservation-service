package com.reservation.tablereservationservice.global.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

	private final PaymentQueueProperties props;

	@Bean
	public DirectExchange paymentExchange() {
		return new DirectExchange(props.getExchange());
	}

	@Bean
	public DirectExchange paymentDlx() {
		return new DirectExchange(props.getDlx());
	}

	@Bean
	public Queue paymentQueue() {
		return QueueBuilder.durable(props.getQueue())
				.withArgument("x-dead-letter-exchange", props.getDlx())
				.withArgument("x-dead-letter-routing-key", props.getDlq())
				.build();
	}

	@Bean
	public Queue paymentDlq() {
		return QueueBuilder.durable(props.getDlq()).build();
	}

	@Bean
	public Binding paymentBinding() {
		return BindingBuilder.bind(paymentQueue()).to(paymentExchange()).with(props.getRoutingKey());
	}

	@Bean
	public Binding paymentDlqBinding() {
		return BindingBuilder.bind(paymentDlq()).to(paymentDlx()).with(props.getDlq());
	}

	@Bean
	public Jackson2JsonMessageConverter messageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMessageConverter(messageConverter());
		return template;
	}
}
