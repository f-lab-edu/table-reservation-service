package com.reservation.tablereservationservice.infrastructure.payment.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reservation.tablereservationservice.application.payment.PaymentClient;
import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.fixture.ReservationFixture;

@ExtendWith(MockitoExtension.class)
class CancelQueueConsumerTest {

	private static final Long RESERVATION_ID = 1L;
	private static final String PAYMENT_KEY = "toss-payment-key";

	@Mock
	private ReservationRepository reservationRepository;
	@Mock
	private PaymentClient paymentClient;
	@Mock
	private ReservationService reservationService;

	@InjectMocks
	private CancelQueueConsumer consumer;

	private CancelQueueMessage message;

	@BeforeEach
	void setUp() {
		message = CancelQueueMessage.of(RESERVATION_ID);
	}

	@Test
	@DisplayName("paymentKey 없음 - cancel() 미호출, confirmCancel() 호출")
	void handleCancelConfirm_success_withoutPaymentKey() {
		Reservation reservation = ReservationFixture.cancelPending()
				.reservationId(RESERVATION_ID)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);

		assertThatCode(() -> consumer.handleCancelConfirm(message)).doesNotThrowAnyException();

		verify(paymentClient, never()).cancel(any(), any());
		verify(reservationService).confirmCancel(reservation);
	}

	@Test
	@DisplayName("paymentKey 있음 - cancel() 호출 후 confirmCancel() 호출")
	void handleCancelConfirm_success_withPaymentKey() {
		Reservation reservation = ReservationFixture.cancelPending()
				.reservationId(RESERVATION_ID)
				.paymentKey(PAYMENT_KEY)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);
		willDoNothing().given(paymentClient).cancel(PAYMENT_KEY, RESERVATION_ID);

		assertThatCode(() -> consumer.handleCancelConfirm(message)).doesNotThrowAnyException();

		verify(paymentClient).cancel(PAYMENT_KEY, RESERVATION_ID);
		verify(reservationService).confirmCancel(reservation);
	}

	@Test
	@DisplayName("이미 CANCELED - 중복 메시지이므로 ack 처리 (confirmCancel 미호출)")
	void handleCancelConfirm_alreadyCanceled_silentAck() {
		Reservation reservation = ReservationFixture.canceled()
				.reservationId(RESERVATION_ID)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);

		assertThatCode(() -> consumer.handleCancelConfirm(message)).doesNotThrowAnyException();

		verifyNoInteractions(paymentClient, reservationService);
	}

	@Test
	@DisplayName("비정상 상태(PENDING 등) - 잘못된 메시지이므로 ack 처리 (confirmCancel 미호출)")
	void handleCancelConfirm_abnormalStatus_silentAck() {
		Reservation reservation = ReservationFixture.pending()
				.reservationId(RESERVATION_ID)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);

		assertThatCode(() -> consumer.handleCancelConfirm(message)).doesNotThrowAnyException();

		verifyNoInteractions(paymentClient, reservationService);
	}

	@Test
	@DisplayName("confirmCancel 예외 - re-throw하여 nack 처리")
	void handleCancelConfirm_exception_rethrows() {
		Reservation reservation = ReservationFixture.cancelPending()
				.reservationId(RESERVATION_ID)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);
		RuntimeException cause = new RuntimeException("DB connection error");
		willThrow(cause).given(reservationService).confirmCancel(reservation);

		assertThatThrownBy(() -> consumer.handleCancelConfirm(message)).isSameAs(cause);
	}

	@Test
	@DisplayName("paymentClient.cancel 예외 - re-throw하여 nack 처리 (confirmCancel 미호출)")
	void handleCancelConfirm_paymentCancelFails_rethrows() {
		Reservation reservation = ReservationFixture.cancelPending()
				.reservationId(RESERVATION_ID)
				.paymentKey(PAYMENT_KEY)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);
		RuntimeException cause = new RuntimeException("Toss API timeout");
		willThrow(cause).given(paymentClient).cancel(PAYMENT_KEY, RESERVATION_ID);

		assertThatThrownBy(() -> consumer.handleCancelConfirm(message)).isSameAs(cause);

		verify(reservationService, never()).confirmCancel(any());
	}
}
