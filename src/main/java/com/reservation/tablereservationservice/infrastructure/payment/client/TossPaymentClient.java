package com.reservation.tablereservationservice.infrastructure.payment.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.tablereservationservice.application.payment.PaymentClient;
import com.reservation.tablereservationservice.application.payment.PaymentRequest;
import com.reservation.tablereservationservice.application.payment.PaymentResult;
import com.reservation.tablereservationservice.global.config.TossPaymentProperties;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.PaymentException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentClient implements PaymentClient {

	private static final String ALREADY_PROCESSED = "ALREADY_PROCESSED_PAYMENT";

	private final TossPaymentProperties properties;
	private final RestClient tossRestClient;
	private final ObjectMapper objectMapper;

	@Override
	@CircuitBreaker(name = "tossPayment")
	@Retry(name = "tossPayment")
	public PaymentResult confirm(PaymentRequest request) {
		try {
			return tossRestClient
					.post()
					.uri(properties.getConfirmUrl())
					.body(request)
					.retrieve()
					.body(PaymentResult.class);
		} catch (HttpClientErrorException e) {
			return handleClientError(request, e);
		}
	}

	@Override
	public PaymentResult queryByPaymentKey(String paymentKey) {
		PaymentResult result;
		try {
			result = tossRestClient
					.get()
					.uri(properties.getPaymentBaseUrl() + "/" + paymentKey)
					.retrieve()
					.body(PaymentResult.class);
		} catch (Exception e) {
			log.error("[TOSS] paymentKey 단건 조회 실패 paymentKey={}", paymentKey, e);
			throw new PaymentException(ErrorCode.TOSS_API_UNAVAILABLE);
		}

		if (result == null) {
			log.warn("[TOSS] paymentKey 단건 조회 응답 없음 paymentKey={}", paymentKey);
			throw new PaymentException(ErrorCode.TOSS_API_UNAVAILABLE);
		}

		return result;
	}

	private PaymentResult handleClientError(PaymentRequest request, HttpClientErrorException e) {
		TossErrorResponse error = parseError(e);

		if (ALREADY_PROCESSED.equals(error.getCode())) {
			log.warn("[TOSS] 이미 처리된 결제 — paymentKey로 단건 조회 paymentKey={}", request.getPaymentKey());
			PaymentResult result = queryByPaymentKey(request.getPaymentKey());

			if (!result.isDone()) {
				log.warn("[TOSS] 단건 조회 결제 미완료 paymentKey={}", request.getPaymentKey());
				throw new PaymentException(ErrorCode.PAYMENT_FAILED);
			}
			return result;
		}

		log.warn("[TOSS] 결제 승인 거절 orderId={} code={} message={}", request.getOrderId(), error.getCode(), error.getMessage());
		throw new PaymentException(ErrorCode.PAYMENT_FAILED);
	}

	private TossErrorResponse parseError(HttpClientErrorException e) {
		try {
			return objectMapper.readValue(e.getResponseBodyAsString(), TossErrorResponse.class);
		} catch (Exception parseEx) {
			log.warn("[TOSS] 에러 응답 파싱 실패 status={}", e.getStatusCode(), parseEx);
			return new TossErrorResponse("UNKNOWN", e.getMessage());
		}
	}
}
