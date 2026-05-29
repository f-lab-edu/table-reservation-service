package com.reservation.tablereservationservice.application.reservation.scheduler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reservation.tablereservationservice.application.payment.PaymentClient;
import com.reservation.tablereservationservice.application.payment.PaymentResult;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.fixture.ReservationFixture;
import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.PaymentException;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.PaymentQueueMessage;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.PaymentQueuePublisher;
import com.reservation.tablereservationservice.infrastructure.redis.ReservationPublisher;

@ExtendWith(MockitoExtension.class)
class PendingReservationExpireSchedulerTest {

	private static final int THRESHOLD_SECONDS = 600;
	private static final Long SLOT_ID = 10L;
	private static final int PARTY_SIZE = 2;
	private static final LocalDate VISIT_DATE = LocalDate.of(2030, 1, 1);
	private static final String PAYMENT_KEY = "toss-key-1";

	@Mock private ReservationRepository reservationRepository;
	@Mock private DailySlotCapacityRepository dailySlotCapacityRepository;
	@Mock private ReservationPublisher reservationPublisher;
	@Mock private ReservationStreamProperties streamProperties;
	@Mock private PaymentClient paymentClient;
	@Mock private PaymentQueuePublisher paymentQueuePublisher;

	@InjectMocks
	private PendingReservationExpireScheduler scheduler;

	private Reservation pendingReservation;

	@BeforeEach
	void setUp() {
		given(streamProperties.getPendingExpireThresholdSeconds()).willReturn(THRESHOLD_SECONDS);

		pendingReservation = ReservationFixture.pending()
				.reservationId(1L)
				.slotId(SLOT_ID)
				.partySize(PARTY_SIZE)
				.visitAt(VISIT_DATE.atTime(19, 0))
				.idempotencyKey("order-uuid-1")
				.paymentKey(PAYMENT_KEY)
				.build();
	}

	@Test
	@DisplayName("만료 대상 없음 - 아무것도 호출하지 않는다")
	void expirePendingReservations_noExpired_doesNothing() {
		given(reservationRepository.findPendingBefore(any(LocalDateTime.class))).willReturn(List.of());

		scheduler.expirePendingReservations();

		verifyNoInteractions(paymentClient, paymentQueuePublisher, dailySlotCapacityRepository, reservationPublisher);
	}

	@Test
	@DisplayName("paymentKey 없음 - Toss 조회 없이 즉시 PAYMENT_FAILED + 좌석 복원")
	void expirePendingReservations_noPaymentKey_failsWithoutTossQuery() {
		Reservation noKeyReservation = ReservationFixture.pending()
				.reservationId(2L)
				.slotId(SLOT_ID)
				.partySize(PARTY_SIZE)
				.visitAt(VISIT_DATE.atTime(19, 0))
				.idempotencyKey("order-uuid-2")
				.build();

		given(reservationRepository.findPendingBefore(any())).willReturn(List.of(noKeyReservation));
		given(reservationRepository.updateStatusConditional(2L, ReservationStatus.PENDING, ReservationStatus.PAYMENT_FAILED)).willReturn(1);

		scheduler.expirePendingReservations();

		verify(reservationRepository).updateStatusConditional(2L, ReservationStatus.PENDING, ReservationStatus.PAYMENT_FAILED);
		verify(dailySlotCapacityRepository).incrementRemainingCount(SLOT_ID, VISIT_DATE, PARTY_SIZE);
		verify(reservationPublisher).releaseSeat(SLOT_ID, VISIT_DATE, PARTY_SIZE);
		verifyNoInteractions(paymentClient, paymentQueuePublisher);
	}

	@Test
	@DisplayName("Toss 조회 성공 (DONE) - payment-queue 발행, 좌석 복원 없음")
	void expirePendingReservations_tossQueryDone_publishesMessage() {
		PaymentResult doneResult = new PaymentResult(
				PAYMENT_KEY, "order-uuid-1", 10000,
				OffsetDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC),
				"DONE"
		);
		given(reservationRepository.findPendingBefore(any())).willReturn(List.of(pendingReservation));
		given(paymentClient.queryByPaymentKey(PAYMENT_KEY)).willReturn(doneResult);

		scheduler.expirePendingReservations();

		ArgumentCaptor<PaymentQueueMessage> captor = ArgumentCaptor.forClass(PaymentQueueMessage.class);
		verify(paymentQueuePublisher).publish(captor.capture());
		assertThat(captor.getValue().getReservationId()).isEqualTo(1L);
		assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("order-uuid-1");

		verifyNoInteractions(dailySlotCapacityRepository, reservationPublisher);
	}

	@Test
	@DisplayName("Toss 조회 성공 (미완료) - PAYMENT_FAILED + 좌석 복원")
	void expirePendingReservations_tossQueryNotDone_failsReservation() {
		PaymentResult notDoneResult = new PaymentResult(null, "order-uuid-1", null, null, "ABORTED");
		given(reservationRepository.findPendingBefore(any())).willReturn(List.of(pendingReservation));
		given(paymentClient.queryByPaymentKey(PAYMENT_KEY)).willReturn(notDoneResult);
		given(reservationRepository.updateStatusConditional(1L, ReservationStatus.PENDING, ReservationStatus.PAYMENT_FAILED)).willReturn(1);

		scheduler.expirePendingReservations();

		verify(reservationRepository).updateStatusConditional(1L, ReservationStatus.PENDING, ReservationStatus.PAYMENT_FAILED);
		verify(dailySlotCapacityRepository).incrementRemainingCount(SLOT_ID, VISIT_DATE, PARTY_SIZE);
		verify(reservationPublisher).releaseSeat(SLOT_ID, VISIT_DATE, PARTY_SIZE);
		verify(paymentQueuePublisher, never()).publish(any());
	}

	@Test
	@DisplayName("Toss 조회 실패 (PaymentException) - PAYMENT_FAILED + 좌석 복원")
	void expirePendingReservations_tossQueryFails_failsReservation() {
		given(reservationRepository.findPendingBefore(any())).willReturn(List.of(pendingReservation));
		given(paymentClient.queryByPaymentKey(PAYMENT_KEY)).willThrow(new PaymentException(ErrorCode.TOSS_API_UNAVAILABLE));
		given(reservationRepository.updateStatusConditional(1L, ReservationStatus.PENDING, ReservationStatus.PAYMENT_FAILED)).willReturn(1);

		scheduler.expirePendingReservations();

		verify(reservationRepository).updateStatusConditional(1L, ReservationStatus.PENDING, ReservationStatus.PAYMENT_FAILED);
		verify(dailySlotCapacityRepository).incrementRemainingCount(SLOT_ID, VISIT_DATE, PARTY_SIZE);
		verify(reservationPublisher).releaseSeat(SLOT_ID, VISIT_DATE, PARTY_SIZE);
	}

	@Test
	@DisplayName("이미 처리된 예약 - 0 rows 시 좌석 복원 없이 건너뜀 (중복 처리 방지)")
	void expirePendingReservations_alreadyProcessed_skipsCapacityRestore() {
		given(reservationRepository.findPendingBefore(any())).willReturn(List.of(pendingReservation));
		given(paymentClient.queryByPaymentKey(PAYMENT_KEY)).willThrow(new PaymentException(ErrorCode.TOSS_API_UNAVAILABLE));
		given(reservationRepository.updateStatusConditional(1L, ReservationStatus.PENDING, ReservationStatus.PAYMENT_FAILED)).willReturn(0);

		scheduler.expirePendingReservations();

		verify(dailySlotCapacityRepository, never()).incrementRemainingCount(anyLong(), any(), anyInt());
		verify(reservationPublisher, never()).releaseSeat(anyLong(), any(), anyInt());
	}

	@Test
	@DisplayName("여러 건 - 모두 독립적으로 처리된다")
	void expirePendingReservations_multipleExpired_processesAll() {
		Reservation r1 = ReservationFixture.pending().reservationId(1L).slotId(SLOT_ID).partySize(2).visitAt(VISIT_DATE.atTime(18, 0)).build();
		Reservation r2 = ReservationFixture.pending().reservationId(2L).slotId(SLOT_ID).partySize(1).visitAt(VISIT_DATE.atTime(19, 0)).build();
		Reservation r3 = ReservationFixture.pending().reservationId(3L).slotId(SLOT_ID).partySize(3).visitAt(VISIT_DATE.atTime(20, 0)).build();

		given(reservationRepository.findPendingBefore(any())).willReturn(List.of(r1, r2, r3));
		given(reservationRepository.updateStatusConditional(anyLong(), eq(ReservationStatus.PENDING), eq(ReservationStatus.PAYMENT_FAILED))).willReturn(1);

		scheduler.expirePendingReservations();

		verify(reservationRepository, times(3)).updateStatusConditional(anyLong(), eq(ReservationStatus.PENDING), eq(ReservationStatus.PAYMENT_FAILED));
		verify(dailySlotCapacityRepository, times(3)).incrementRemainingCount(eq(SLOT_ID), any(), anyInt());
		verify(reservationPublisher, times(3)).releaseSeat(eq(SLOT_ID), any(), anyInt());
		verifyNoInteractions(paymentClient);
	}

	@Test
	@DisplayName("만료 기준 시각 - 현재로부터 정확히 10분(600초) 이전으로 계산된다")
	void expirePendingReservations_queryThreshold_isBeforeThreshold() {
		given(reservationRepository.findPendingBefore(any())).willReturn(List.of());

		LocalDateTime before = LocalDateTime.now().minusSeconds(THRESHOLD_SECONDS);

		scheduler.expirePendingReservations();

		ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(reservationRepository).findPendingBefore(captor.capture());
		assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusSeconds(THRESHOLD_SECONDS - 1));
		assertThat(captor.getValue()).isAfterOrEqualTo(before.minusSeconds(1));
	}
}