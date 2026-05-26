package com.reservation.tablereservationservice.infrastructure.payment.client;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.reservation.tablereservationservice.application.payment.PaymentClient;
import com.reservation.tablereservationservice.application.payment.PaymentRequest;
import com.reservation.tablereservationservice.application.payment.PaymentResult;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.PaymentException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TossPaymentClient implements PaymentClient {

	@Value("${toss.payments.secret-key}")
	private String secretKey;

	@Value("${toss.payments.confirm-url}")
	private String confirmUrl;

	private RestClient restClient;

	@PostConstruct
	void init() {
		String encoded = Base64.getEncoder()
				.encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.build();

		restClient = RestClient.builder()
				.requestFactory(new JdkClientHttpRequestFactory(httpClient))
				.defaultHeader("Authorization", "Basic " + encoded)
				.defaultHeader("Content-Type", "application/json")
				.build();
	}

	@Override
	@CircuitBreaker(name = "tossPayment", fallbackMethod = "fallback")
	@Retry(name = "tossPayment")
	public PaymentResult confirm(PaymentRequest request) {
		return restClient
				.post()
				.uri(confirmUrl)
				.body(request)
				.retrieve()
				.body(PaymentResult.class);
	}

	private PaymentResult fallback(PaymentRequest request, Exception e) {
		log.error("[TOSS] 결제 승인 실패 orderId={}", request.getOrderId(), e);
		throw new PaymentException(ErrorCode.TOSS_API_UNAVAILABLE);
	}
}
