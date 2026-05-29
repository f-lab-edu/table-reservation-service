package com.reservation.tablereservationservice.infrastructure.payment.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.reservation.tablereservationservice.application.payment.PaymentResult;
import com.reservation.tablereservationservice.application.payment.service.PaymentConfirmationService;
import com.reservation.tablereservationservice.fixture.ReservationFixture;

@ExtendWith(MockitoExtension.class)
class PaymentQueueConsumerTest {

	@Mock
	private PaymentConfirmationService paymentConfirmationService;

	@InjectMocks
	private PaymentQueueConsumer listener;

	private PaymentQueueMessage message;

	@BeforeEach
	void setUp() {
		PaymentResult result = new PaymentResult(
				"toss-key", "order-uuid", 10000,
				OffsetDateTime.of(2030, 1, 1, 19, 0, 0, 0, ZoneOffset.ofHours(9)),
				"DONE"
		);
		message = PaymentQueueMessage.ofConfirmed(
				ReservationFixture.pending().reservationId(1L).build(), "order-uuid", result);
	}

	@Test
	@DisplayName("정상 처리 - approve()을 호출하고 예외 없이 종료한다")
	void handlePaymentConfirm_success() {
		willDoNothing().given(paymentConfirmationService).confirm(message);

		assertThatCode(() -> listener.handlePaymentConfirm(message)).doesNotThrowAnyException();
		verify(paymentConfirmationService).confirm(message);
	}

	@Test
	@DisplayName("기술적 오류 - 예외를 re-throw해 NACK 처리한다")
	void handlePaymentConfirm_technicalError_rethrows() {
		RuntimeException cause = new RuntimeException("DB timeout");
		willThrow(cause).given(paymentConfirmationService).confirm(message);

		assertThatThrownBy(() -> listener.handlePaymentConfirm(message))
				.isSameAs(cause);
	}

	@Test
	@DisplayName("중복 메시지 - DataIntegrityViolationException은 ack 처리한다")
	void handlePaymentConfirm_duplicateMessage_acks() {
		willThrow(new DataIntegrityViolationException("Duplicate entry"))
				.given(paymentConfirmationService).confirm(message);

		assertThatCode(() -> listener.handlePaymentConfirm(message)).doesNotThrowAnyException();
	}
}
