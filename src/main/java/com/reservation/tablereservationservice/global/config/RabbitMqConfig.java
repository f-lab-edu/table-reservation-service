package com.reservation.tablereservationservice.global.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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

	// Consistent Hash Exchange: 동일 slotId 는 항상 같은 파티션 큐로 라우팅
	public static final String RESERVATION_EXCHANGE = "reservation.exchange";
	public static final String RESERVATION_REQUEST_QUEUE_PREFIX = "reservation.request.queue.";

	// 파티션 큐 이름 상수 (컴파일 타임 상수 → @RabbitListener 어노테이션에서 사용 가능)
	public static final String RESERVATION_REQUEST_QUEUE_1 = RESERVATION_REQUEST_QUEUE_PREFIX + "1";
	public static final String RESERVATION_REQUEST_QUEUE_2 = RESERVATION_REQUEST_QUEUE_PREFIX + "2";
	public static final String RESERVATION_REQUEST_QUEUE_3 = RESERVATION_REQUEST_QUEUE_PREFIX + "3";
	public static final String RESERVATION_REQUEST_QUEUE_4 = RESERVATION_REQUEST_QUEUE_PREFIX + "4";

	// Dead Letter Exchange / Queue: 기술적 장애(예외 재던짐) 시 NACK → DLX → DLQ로 적재
	public static final String RESERVATION_DLX = "reservation.dlx";
	public static final String RESERVATION_DLQ = "reservation.dlq";
	public static final String RESERVATION_DLQ_ROUTING_KEY = "reservation.dead";

	// x-consistent-hash 타입 exchange: 라우팅 키(slotId)를 해시하여 파티션 큐로 분산
	@Bean
	public CustomExchange reservationExchange() {
		return new CustomExchange(RESERVATION_EXCHANGE, "x-consistent-hash", true, false);
	}

	@Bean
	public Queue reservationPartitionQueue1() {
		return buildPartitionQueue(1);
	}

	@Bean
	public Queue reservationPartitionQueue2() {
		return buildPartitionQueue(2);
	}

	@Bean
	public Queue reservationPartitionQueue3() {
		return buildPartitionQueue(3);
	}

	@Bean
	public Queue reservationPartitionQueue4() {
		return buildPartitionQueue(4);
	}

	// 바인딩 가중치 = 해시 링의 가상 노드 수. 낮으면 라우팅 키가 한 큐로 편중될 수 있음
	private static final String PARTITION_BINDING_WEIGHT = "10";

	@Bean
	public Binding reservationPartitionBinding1(CustomExchange reservationExchange) {
		return BindingBuilder.bind(reservationPartitionQueue1()).to(reservationExchange).with(PARTITION_BINDING_WEIGHT).noargs();
	}

	@Bean
	public Binding reservationPartitionBinding2(CustomExchange reservationExchange) {
		return BindingBuilder.bind(reservationPartitionQueue2()).to(reservationExchange).with(PARTITION_BINDING_WEIGHT).noargs();
	}

	@Bean
	public Binding reservationPartitionBinding3(CustomExchange reservationExchange) {
		return BindingBuilder.bind(reservationPartitionQueue3()).to(reservationExchange).with(PARTITION_BINDING_WEIGHT).noargs();
	}

	@Bean
	public Binding reservationPartitionBinding4(CustomExchange reservationExchange) {
		return BindingBuilder.bind(reservationPartitionQueue4()).to(reservationExchange).with(PARTITION_BINDING_WEIGHT).noargs();
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

	@Bean
	public DirectExchange reservationDlx() {
		return new DirectExchange(RESERVATION_DLX);
	}

	@Bean
	public Queue reservationDlq() {
		return new Queue(RESERVATION_DLQ, true);
	}

	@Bean
	public Binding reservationDlqBinding() {
		return BindingBuilder.bind(reservationDlq())
			.to(reservationDlx())
			.with(RESERVATION_DLQ_ROUTING_KEY);
	}

	private Queue buildPartitionQueue(int partition) {
		return QueueBuilder.durable(RESERVATION_REQUEST_QUEUE_PREFIX + partition)
			.withArgument("x-dead-letter-exchange", RESERVATION_DLX)
			.withArgument("x-dead-letter-routing-key", RESERVATION_DLQ_ROUTING_KEY)
			.build();
	}
}
