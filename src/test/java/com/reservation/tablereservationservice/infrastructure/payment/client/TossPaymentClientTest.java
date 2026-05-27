package com.reservation.tablereservationservice.infrastructure.payment.client;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reservation.tablereservationservice.application.payment.PaymentRequest;
import com.reservation.tablereservationservice.application.payment.PaymentResult;
import com.reservation.tablereservationservice.global.config.TossPaymentProperties;
import com.reservation.tablereservationservice.global.exception.PaymentException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class TossPaymentClientTest {

	private MockWebServer mockServer;
	private TossPaymentClient client;

	@BeforeEach
	void setUp() throws Exception {
		mockServer = new MockWebServer();
		mockServer.start();

		TossPaymentProperties properties = new TossPaymentProperties();
		properties.setConfirmUrl(mockServer.url("/v1/payments/approve").toString());
		properties.setPaymentBaseUrl(mockServer.url("/v1/payments").toString());

		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		RestClient restClient = RestClient.builder().build();

		client = new TossPaymentClient(properties, restClient, objectMapper);
	}

	@AfterEach
	void tearDown() throws Exception {
		mockServer.shutdown();
	}

	@Test
	@DisplayName("200 응답 - PaymentResult를 정상적으로 파싱한다")
	void confirm_success_parsesPaymentResult() {
		mockServer.enqueue(new MockResponse()
				.setResponseCode(200)
				.addHeader("Content-Type", "application/json")
				.setBody("""
						{
						  "paymentKey": "toss-key-123",
						  "orderId": "order-uuid-1",
						  "totalAmount": 10000,
						  "approvedAt": "2030-01-01T19:00:00+09:00"
						}
						"""));

		PaymentResult result = client.confirm(
				PaymentRequest.of("toss-key-123", "order-uuid-1", 10000));

		assertThat(result.getPaymentKey()).isEqualTo("toss-key-123");
		assertThat(result.getOrderId()).isEqualTo("order-uuid-1");
		assertThat(result.getTotalAmount()).isEqualTo(10000);
		assertThat(result.getApprovedAt()).isNotNull();
	}

	@Test
	@DisplayName("4xx 응답 (카드 거절 등) - PaymentException(PAYMENT_FAILED)이 발생한다")
	void confirm_cardDeclined_throwsPaymentException() {
		mockServer.enqueue(new MockResponse()
				.setResponseCode(400)
				.addHeader("Content-Type", "application/json")
				.setBody("""
						{"code":"CARD_DECLINED","message":"카드 한도를 초과했습니다."}
						"""));

		assertThatThrownBy(() -> client.confirm(
				PaymentRequest.of("bad-key", "order-1", 10000)))
				.isInstanceOf(PaymentException.class);
	}

	@Test
	@DisplayName("ALREADY_PROCESSED_PAYMENT - paymentKey로 단건 조회 결과를 반환한다")
	void confirm_alreadyProcessed_queriesAndReturnsResult() {
		mockServer.enqueue(new MockResponse()
				.setResponseCode(400)
				.addHeader("Content-Type", "application/json")
				.setBody("""
						{"code":"ALREADY_PROCESSED_PAYMENT","message":"이미 처리된 결제 입니다."}
						"""));

		mockServer.enqueue(new MockResponse()
				.setResponseCode(200)
				.addHeader("Content-Type", "application/json")
				.setBody("""
						{
						  "paymentKey": "toss-key-123",
						  "orderId": "order-uuid-1",
						  "totalAmount": 10000,
						  "approvedAt": "2030-01-01T19:00:00+09:00",
						  "status": "DONE"
						}
						"""));

		PaymentResult result = client.confirm(
				PaymentRequest.of("toss-key-123", "order-uuid-1", 10000));

		assertThat(result.getPaymentKey()).isEqualTo("toss-key-123");
		assertThat(result.getOrderId()).isEqualTo("order-uuid-1");
		assertThat(result.getTotalAmount()).isEqualTo(10000);
	}

	@Test
	@DisplayName("5xx 응답 - HttpServerErrorException이 발생한다")
	void confirm_5xx_throwsHttpServerErrorException() {
		mockServer.enqueue(new MockResponse().setResponseCode(500));

		assertThatThrownBy(() -> client.confirm(
				PaymentRequest.of("key", "order-1", 10000)))
				.isInstanceOf(HttpServerErrorException.class);
	}
}
