package com.reservation.tablereservationservice.global.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

	private final PaymentQueueProperties props;
	private final CancelQueueProperties cancelProps;

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
	public DirectExchange cancelExchange() {
		return new DirectExchange(cancelProps.getExchange());
	}

	@Bean
	public DirectExchange cancelDlx() {
		return new DirectExchange(cancelProps.getDlx());
	}

	@Bean
	public Queue cancelQueue() {
		return QueueBuilder.durable(cancelProps.getQueue())
				.withArgument("x-dead-letter-exchange", cancelProps.getDlx())
				.withArgument("x-dead-letter-routing-key", cancelProps.getDlq())
				.build();
	}

	@Bean
	public Queue cancelDlq() {
		return QueueBuilder.durable(cancelProps.getDlq()).build();
	}

	@Bean
	public Binding cancelBinding() {
		return BindingBuilder.bind(cancelQueue()).to(cancelExchange()).with(cancelProps.getRoutingKey());
	}

	@Bean
	public Binding cancelDlqBinding() {
		return BindingBuilder.bind(cancelDlq()).to(cancelDlx()).with(cancelProps.getDlq());
	}

	// payment-queue: retry 3회 후 DLQ
	@Bean
	public SimpleRabbitListenerContainerFactory paymentContainerFactory(ConnectionFactory connectionFactory) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setMessageConverter(messageConverter());
		factory.setDefaultRequeueRejected(false);
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3));
		factory.setRetryTemplate(retryTemplate);
		return factory;
	}

	// cancel-queue: retry 없이 즉시 DLQ (@Retry가 이미 3회, RecoveryScheduler가 백업)
	@Bean
	public SimpleRabbitListenerContainerFactory cancelContainerFactory(ConnectionFactory connectionFactory) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setMessageConverter(messageConverter());
		factory.setDefaultRequeueRejected(false);
		return factory;
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
