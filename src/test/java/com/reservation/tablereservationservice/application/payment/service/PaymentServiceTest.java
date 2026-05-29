package com.reservation.tablereservationservice.application.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import com.reservation.tablereservationservice.application.payment.PaymentClient;
import com.reservation.tablereservationservice.application.payment.PaymentRequest;
import com.reservation.tablereservationservice.application.payment.PaymentResult;

import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.fixture.ReservationFixture;
import com.reservation.tablereservationservice.fixture.RestaurantSlotFixture;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.PaymentException;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.PaymentQueueMessage;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.PaymentQueuePublisher;
import com.reservation.tablereservationservice.presentation.payment.dto.PaymentConfirmRequestDto;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long SLOT_ID = 10L;
	private static final Long RESERVATION_ID = 100L;
	private static final String IDEMPOTENCY_KEY = "order-uuid-1234";
	private static final String PAYMENT_KEY = "toss-payment-key";
	private static final int DEPOSIT_PER_PERSON = 5000;
	private static final int PARTY_SIZE = 2;
	private static final int EXPECTED_AMOUNT = DEPOSIT_PER_PERSON * PARTY_SIZE; // 10000

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private RestaurantSlotRepository restaurantSlotRepository;

	@Mock
	private PaymentClient paymentClient;

	@Mock
	private PaymentQueuePublisher paymentQueuePublisher;

	@InjectMocks
	private PaymentService paymentService;

	private Reservation pendingReservation;
	private RestaurantSlot slot;
	private PaymentConfirmRequestDto requestDto;

	@BeforeEach
	void setUp() {
		slot = RestaurantSlotFixture.slot()
				.slotId(SLOT_ID)
				.depositPerPerson(DEPOSIT_PER_PERSON)
				.build();

		pendingReservation = ReservationFixture.pending()
				.reservationId(RESERVATION_ID)
				.userId(USER_ID)
				.slotId(SLOT_ID)
				.partySize(PARTY_SIZE)
				.idempotencyKey(IDEMPOTENCY_KEY)
				.build();

		requestDto = new PaymentConfirmRequestDto(PAYMENT_KEY, IDEMPOTENCY_KEY, EXPECTED_AMOUNT);
	}

	@Test
	@DisplayName("결제 확정 성공 - 결제 승인 후 큐에 메시지를 발행한다")
	void approve_success() {
		// given
		OffsetDateTime approvedAt = OffsetDateTime.of(2030, 1, 1, 19, 0, 0, 0, ZoneOffset.ofHours(9));
		PaymentResult tossResult = new PaymentResult(PAYMENT_KEY, IDEMPOTENCY_KEY, EXPECTED_AMOUNT, approvedAt, "DONE");

		given(reservationRepository.fetchByIdempotencyKey(IDEMPOTENCY_KEY)).willReturn(pendingReservation);
		given(restaurantSlotRepository.fetchById(SLOT_ID)).willReturn(slot);
		given(paymentClient.confirm(any(PaymentRequest.class))).willReturn(tossResult);

		// when
		paymentService.approve(USER_ID, requestDto);

		// then
		verify(reservationRepository).updatePaymentKey(pendingReservation);
		ArgumentCaptor<PaymentQueueMessage> messageCaptor = ArgumentCaptor.forClass(PaymentQueueMessage.class);
		verify(paymentQueuePublisher).publish(messageCaptor.capture());

		PaymentQueueMessage published = messageCaptor.getValue();
		assertThat(published.getReservationId()).isEqualTo(RESERVATION_ID);
		assertThat(published.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
		assertThat(published.getPaymentKey()).isEqualTo(PAYMENT_KEY);
		assertThat(published.getAmount()).isEqualTo(EXPECTED_AMOUNT);
		assertThat(published.getApprovedAt()).isEqualTo(approvedAt.toLocalDateTime());
	}

	@Test
	@DisplayName("결제 확정 실패 - 예약을 찾을 수 없으면 예외가 발생한다")
	void approve_fail_reservationNotFound() {
		// given
		given(reservationRepository.fetchByIdempotencyKey(IDEMPOTENCY_KEY))
				.willThrow(new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, "예약"));

		// when & then
		assertThatThrownBy(() -> paymentService.approve(USER_ID, requestDto))
				.isInstanceOf(PaymentException.class)
				.satisfies(ex -> assertThat(((PaymentException)ex).getErrorCode())
						.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

		verifyNoInteractions(paymentClient, paymentQueuePublisher);
	}

	@Test
	@DisplayName("결제 확정 실패 - 본인 예약이 아니면 접근 금지 예외가 발생한다")
	void approve_fail_notOwner() {
		// given
		Reservation otherUserReservation = ReservationFixture.pending()
				.reservationId(RESERVATION_ID)
				.userId(999L)
				.slotId(SLOT_ID)
				.partySize(PARTY_SIZE)
				.idempotencyKey(IDEMPOTENCY_KEY)
				.build();

		given(reservationRepository.fetchByIdempotencyKey(IDEMPOTENCY_KEY)).willReturn(otherUserReservation);

		// when & then
		assertThatThrownBy(() -> paymentService.approve(USER_ID, requestDto))
				.isInstanceOf(PaymentException.class)
				.satisfies(ex -> assertThat(((PaymentException)ex).getErrorCode())
						.isEqualTo(ErrorCode.ACCESS_DENIED));

		verifyNoInteractions(paymentClient, paymentQueuePublisher);
	}

	@Test
	@DisplayName("결제 확정 성공 - 이미 CONFIRMED인 예약은 멱등 처리한다 (결제 승인 재호출 없음)")
	void approve_alreadyConfirmed_idempotent() {
		// given
		Reservation confirmedReservation = ReservationFixture.confirmed()
				.reservationId(RESERVATION_ID)
				.userId(USER_ID)
				.slotId(SLOT_ID)
				.idempotencyKey(IDEMPOTENCY_KEY)
				.build();

		given(reservationRepository.fetchByIdempotencyKey(IDEMPOTENCY_KEY)).willReturn(confirmedReservation);

		// when & then
		assertThatCode(() -> paymentService.approve(USER_ID, requestDto)).doesNotThrowAnyException();
		verifyNoInteractions(paymentClient, paymentQueuePublisher);
	}

	@Test
	@DisplayName("결제 확정 실패 - PAYMENT_FAILED 예약은 예외가 발생한다")
	void approvePayment_fail_Failed() {
		// given
		Reservation failedReservation = ReservationFixture.paymentFailed()
				.reservationId(RESERVATION_ID)
				.userId(USER_ID)
				.slotId(SLOT_ID)
				.idempotencyKey(IDEMPOTENCY_KEY)
				.build();

		given(reservationRepository.fetchByIdempotencyKey(IDEMPOTENCY_KEY)).willReturn(failedReservation);

		// when & then
		assertThatThrownBy(() -> paymentService.approve(USER_ID, requestDto))
				.isInstanceOf(PaymentException.class)
				.satisfies(ex -> assertThat(((PaymentException)ex).getErrorCode())
						.isEqualTo(ErrorCode.PAYMENT_RESERVATION_FAILED));

		verifyNoInteractions(paymentClient, paymentQueuePublisher);
	}

	@Test
	@DisplayName("결제 확정 실패 - 금액이 슬롯 예약금과 다르면 예외가 발생한다")
	void approve_fail_amountMismatch() {
		// given
		PaymentConfirmRequestDto wrongAmountDto = new PaymentConfirmRequestDto(PAYMENT_KEY, IDEMPOTENCY_KEY, 99999);

		given(reservationRepository.fetchByIdempotencyKey(IDEMPOTENCY_KEY)).willReturn(pendingReservation);
		given(restaurantSlotRepository.fetchById(SLOT_ID)).willReturn(slot);

		// when & then
		assertThatThrownBy(() -> paymentService.approve(USER_ID, wrongAmountDto))
				.isInstanceOf(PaymentException.class)
				.satisfies(ex -> assertThat(((PaymentException)ex).getErrorCode())
						.isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH));

		verifyNoInteractions(paymentClient, paymentQueuePublisher);
	}

	@Test
	@DisplayName("결제 확정 실패 - 5xx 오류 시 TOSS_API_UNAVAILABLE 예외가 발생한다")
	void approve_fail_tossServerError() {
		// given
		given(reservationRepository.fetchByIdempotencyKey(IDEMPOTENCY_KEY)).willReturn(pendingReservation);
		given(restaurantSlotRepository.fetchById(SLOT_ID)).willReturn(slot);
		given(paymentClient.confirm(any(PaymentRequest.class)))
				.willThrow(HttpServerErrorException.class);

		// when & then
		assertThatThrownBy(() -> paymentService.approve(USER_ID, requestDto))
				.isInstanceOf(PaymentException.class)
				.satisfies(ex -> assertThat(((PaymentException)ex).getErrorCode())
						.isEqualTo(ErrorCode.TOSS_API_UNAVAILABLE));

		verify(reservationRepository).updatePaymentKey(pendingReservation);
		verify(paymentQueuePublisher, never()).publish(any());
	}

	@Test
	@DisplayName("결제 확정 실패 - 네트워크 타임아웃 시 TOSS_API_UNAVAILABLE 예외가 발생한다")
	void approve_fail_networkTimeout() {
		given(reservationRepository.fetchByIdempotencyKey(IDEMPOTENCY_KEY)).willReturn(pendingReservation);
		given(restaurantSlotRepository.fetchById(SLOT_ID)).willReturn(slot);
		given(paymentClient.confirm(any(PaymentRequest.class)))
				.willThrow(new ResourceAccessException("Connection timed out"));

		assertThatThrownBy(() -> paymentService.approve(USER_ID, requestDto))
				.isInstanceOf(PaymentException.class)
				.satisfies(ex -> assertThat(((PaymentException)ex).getErrorCode())
						.isEqualTo(ErrorCode.TOSS_API_UNAVAILABLE));

		verify(reservationRepository).updatePaymentKey(pendingReservation);
		verify(paymentQueuePublisher, never()).publish(any());
	}

	@Test
	@DisplayName("결제 확정 실패 - 서킷 브레이커 OPEN 시 TOSS_API_UNAVAILABLE 예외가 발생한다")
	void approve_fail_circuitBreakerOpen() {
		given(reservationRepository.fetchByIdempotencyKey(IDEMPOTENCY_KEY)).willReturn(pendingReservation);
		given(restaurantSlotRepository.fetchById(SLOT_ID)).willReturn(slot);
		given(paymentClient.confirm(any(PaymentRequest.class)))
				.willThrow(CallNotPermittedException.createCallNotPermittedException(
						CircuitBreaker.ofDefaults("test")));

		assertThatThrownBy(() -> paymentService.approve(USER_ID, requestDto))
				.isInstanceOf(PaymentException.class)
				.satisfies(ex -> assertThat(((PaymentException)ex).getErrorCode())
						.isEqualTo(ErrorCode.TOSS_API_UNAVAILABLE));

		verify(reservationRepository).updatePaymentKey(pendingReservation);
		verify(paymentQueuePublisher, never()).publish(any());
	}
}
