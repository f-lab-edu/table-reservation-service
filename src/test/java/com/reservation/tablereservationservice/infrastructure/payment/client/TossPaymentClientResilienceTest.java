package com.reservation.tablereservationservice.infrastructure.payment.client;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.reservation.tablereservationservice.application.payment.PaymentClient;
import com.reservation.tablereservationservice.application.payment.PaymentRequest;
import com.reservation.tablereservationservice.application.payment.PaymentResult;
import com.reservation.tablereservationservice.global.exception.PaymentException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;

@SpringBootTest
@ActiveProfiles("test")
class TossPaymentClientResilienceTest {

	static MockWebServer mockServer;

	static {
		mockServer = new MockWebServer();
		try {
			mockServer.start();
		} catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@DynamicPropertySource
	static void setProperties(DynamicPropertyRegistry registry) {
		registry.add("toss.payments.approve-url", () -> mockServer.url("/v1/payments/approve").toString());
		registry.add("toss.payments.payment-base-url", () -> mockServer.url("/v1/payments").toString());
	}

	@AfterAll
	static void tearDown() throws IOException {
		mockServer.shutdown();
	}

	@Autowired
	private PaymentClient paymentClient;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	private int requestCountBefore;

	@BeforeEach
	void setUp() {
		circuitBreakerRegistry.circuitBreaker("tossPayment").reset();
		requestCountBefore = mockServer.getRequestCount();
	}

	@Test
	@DisplayName("5xx - max-attempts(3)만큼 재시도 후 HttpServerErrorException이 전파된다")
	void retry_exhausted_on_5xx() {
		for (int i = 0; i < 3; i++) {
			mockServer.enqueue(new MockResponse().setResponseCode(500));
		}

		PaymentRequest request = PaymentRequest.of("key", "order-1", 1000);

		assertThatThrownBy(() -> paymentClient.confirm(request))
				.isInstanceOf(HttpServerErrorException.class);

		assertThat(mockServer.getRequestCount() - requestCountBefore).isEqualTo(3);
	}

	@Test
	@DisplayName("4xx (카드 거절 등) - 재시도 없이 즉시 PaymentException이 전파된다")
	void no_retry_on_4xx() {
		mockServer.enqueue(new MockResponse()
				.setResponseCode(400)
				.addHeader("Content-Type", "application/json")
				.setBody("{\"code\":\"CARD_DECLINED\",\"message\":\"카드 한도를 초과했습니다.\"}"));

		PaymentRequest request = PaymentRequest.of("key", "order-1", 1000);

		assertThatThrownBy(() -> paymentClient.confirm(request))
				.isInstanceOf(PaymentException.class);

		assertThat(mockServer.getRequestCount() - requestCountBefore).isEqualTo(1);
	}

	@Test
	@DisplayName("CB OPEN - HTTP 호출 없이 CallNotPermittedException이 전파된다")
	void circuit_breaker_open_blocks_call() {
		circuitBreakerRegistry.circuitBreaker("tossPayment").transitionToOpenState();

		PaymentRequest request = PaymentRequest.of("key", "order-1", 1000);

		assertThatThrownBy(() -> paymentClient.confirm(request))
				.isInstanceOf(CallNotPermittedException.class);

		assertThat(mockServer.getRequestCount() - requestCountBefore).isEqualTo(0);
	}

	@Test
	@DisplayName("retry → ALREADY_PROCESSED_PAYMENT → queryByPaymentKey 복구")
	void retry_alreadyProcessed_recoversViaQuery() {
		// 1차 시도: 네트워크 단절 → ResourceAccessException → @Retry 발동
		mockServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

		// 2차 시도(재시도): ALREADY_PROCESSED_PAYMENT → handleClientError → queryByPaymentKey 호출
		mockServer.enqueue(new MockResponse()
				.setResponseCode(400)
				.addHeader("Content-Type", "application/json")
				.setBody("{\"code\":\"ALREADY_PROCESSED_PAYMENT\",\"message\":\"이미 처리된 결제 입니다.\"}"));

		// queryByPaymentKey 응답
		mockServer.enqueue(new MockResponse()
				.setResponseCode(200)
				.addHeader("Content-Type", "application/json")
				.setBody("""
						{"paymentKey":"toss-key-123","orderId":"order-1","totalAmount":1000,"approvedAt":"2030-01-01T19:00:00+09:00","status":"DONE"}
						"""));

		PaymentRequest request = PaymentRequest.of("toss-key-123", "order-1", 1000);

		PaymentResult result = paymentClient.confirm(request);

		assertThat(result.getPaymentKey()).isEqualTo("toss-key-123");
		// 네트워크 오류 1회 + ALREADY_PROCESSED 1회 + 단건 조회 1회 = 총 3회
		assertThat(mockServer.getRequestCount() - requestCountBefore).isEqualTo(3);
	}

	@Test
	@DisplayName("4xx는 CB 실패율에 집계되지 않아 연속 발생해도 CB가 열리지 않는다")
	void circuit_breaker_ignores_4xx_failures() {
		// sliding-window=4, threshold=100% → 5번 4xx가 와도 CB는 CLOSED 유지
		for (int i = 0; i < 5; i++) {
			mockServer.enqueue(new MockResponse()
					.setResponseCode(400)
					.addHeader("Content-Type", "application/json")
					.setBody("{\"code\":\"CARD_DECLINED\",\"message\":\"카드 한도를 초과했습니다.\"}"));
		}

		PaymentRequest request = PaymentRequest.of("key", "order-1", 1000);

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> paymentClient.confirm(request))
					.isInstanceOf(PaymentException.class);
		}

		assertThat(circuitBreakerRegistry.circuitBreaker("tossPayment").getState())
				.isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	@DisplayName("읽기 타임아웃 - max-attempts(3)만큼 재시도 후 ResourceAccessException이 전파된다")
	void readTimeout_exhausted_triggersRetryAndFails() {
		// read-timeout-seconds=1 (test.yml), NO_RESPONSE → 서버가 응답을 보내지 않아 타임아웃 발생
		for (int i = 0; i < 3; i++) {
			mockServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
		}

		PaymentRequest request = PaymentRequest.of("key", "order-1", 1000);

		assertThatThrownBy(() -> paymentClient.confirm(request))
				.isInstanceOf(ResourceAccessException.class);

		assertThat(mockServer.getRequestCount() - requestCountBefore).isEqualTo(3);
	}

	@Test
	@DisplayName("CB - 연속 실패 후 OPEN으로 전환되고 이후 호출은 차단된다")
	void circuit_breaker_opens_after_threshold_failures() {
		// sliding-window=4, threshold=100% → 4회 실패 시 OPEN
		// 각 호출은 3번 재시도 → 호출 1회당 HTTP 요청 3회
		for (int i = 0; i < 4 * 3; i++) {
			mockServer.enqueue(new MockResponse().setResponseCode(500));
		}

		PaymentRequest request = PaymentRequest.of("key", "order-1", 1000);

		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> paymentClient.confirm(request))
					.isInstanceOf(HttpServerErrorException.class);
		}

		assertThat(circuitBreakerRegistry.circuitBreaker("tossPayment").getState())
				.isEqualTo(CircuitBreaker.State.OPEN);

		// OPEN 상태: 추가 HTTP 요청 없이 즉시 차단
		assertThatThrownBy(() -> paymentClient.confirm(request))
				.isInstanceOf(CallNotPermittedException.class);

		assertThat(mockServer.getRequestCount() - requestCountBefore).isEqualTo(12);
	}
}
