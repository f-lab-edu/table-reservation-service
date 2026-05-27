package com.reservation.tablereservationservice.application.reservation.scheduler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.fixture.DailySlotCapacityFixture;
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
	private static final int INITIAL_REMAINING = 4;
	private static final String PAYMENT_KEY = "toss-key-1";

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private DailySlotCapacityRepository dailySlotCapacityRepository;

	@Mock
	private ReservationPublisher reservationPublisher;

	@Mock
	private ReservationStreamProperties streamProperties;

	@Mock
	private PaymentClient paymentClient;

	@Mock
	private PaymentQueuePublisher paymentQueuePublisher;

	@InjectMocks
	private PendingReservationExpireScheduler scheduler;

	private Reservation pendingReservation;
	private DailySlotCapacity capacity;

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

		capacity = DailySlotCapacityFixture.capacity()
				.capacityId(100L)
				.slotId(SLOT_ID)
				.date(VISIT_DATE)
				.remainingCount(INITIAL_REMAINING)
				.build();
	}

	@Test
	@DisplayName("만료 대상 없음 - 결제 조회 없이 종료한다")
	void expirePendingReservations_noExpired_doesNothing() {
		given(reservationRepository.findPendingBefore(any(LocalDateTime.class))).willReturn(List.of());

		scheduler.expirePendingReservations();

		verifyNoInteractions(paymentClient, paymentQueuePublisher, dailySlotCapacityRepository, reservationPublisher);
	}

	@Test
	@DisplayName("paymentKey 없음 - 결제 조회 없이 즉시 PAYMENT_FAILED로 처리한다")
	void expirePendingReservations_noPaymentKey_failsWithoutTossQuery() {
		Reservation noKeyReservation = ReservationFixture.pending()
				.reservationId(2L)
				.slotId(SLOT_ID)
				.partySize(PARTY_SIZE)
				.visitAt(VISIT_DATE.atTime(19, 0))
				.idempotencyKey("order-uuid-2")
				.build();
		given(reservationRepository.findPendingBefore(any())).willReturn(List.of(noKeyReservation));
		given(dailySlotCapacityRepository.findBySlotIdAndDate(SLOT_ID, VISIT_DATE)).willReturn(Optional.of(capacity));

		scheduler.expirePendingReservations();

		assertThat(noKeyReservation.getStatus()).isEqualTo(ReservationStatus.PAYMENT_FAILED);
		verify(reservationRepository).updateStatus(noKeyReservation);
		verifyNoInteractions(paymentClient, paymentQueuePublisher);
	}

	@Test
	@DisplayName("결제 조회 성공 (DONE) - 큐에 메시지를 발행하고 예약 상태를 바꾸지 않는다")
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

		assertThat(pendingReservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
		verifyNoInteractions(dailySlotCapacityRepository, reservationPublisher);
	}

	@Test
	@DisplayName("Toss 조회 성공 (미완료) - PAYMENT_FAILED로 처리하고 좌석을 복원한다")
	void expirePendingReservations_tossQueryNotDone_failsReservation() {
		PaymentResult notDoneResult = new PaymentResult(
				null, "order-uuid-1", null, null, "ABORTED"
		);
		given(reservationRepository.findPendingBefore(any())).willReturn(List.of(pendingReservation));
		given(paymentClient.queryByPaymentKey(PAYMENT_KEY)).willReturn(notDoneResult);
		given(dailySlotCapacityRepository.findBySlotIdAndDate(SLOT_ID, VISIT_DATE)).willReturn(Optional.of(capacity));

		scheduler.expirePendingReservations();

		assertThat(pendingReservation.getStatus()).isEqualTo(ReservationStatus.PAYMENT_FAILED);
		verify(reservationRepository).updateStatus(pendingReservation);
		verify(paymentQueuePublisher, never()).publish(any());
	}

	@Test
	@DisplayName("Toss 조회 실패 (PaymentException) - PAYMENT_FAILED로 처리하고 좌석을 복원한다")
	void expirePendingReservations_tossQueryFails_failsReservation() {
		given(reservationRepository.findPendingBefore(any())).willReturn(List.of(pendingReservation));
		given(paymentClient.queryByPaymentKey(PAYMENT_KEY)).willThrow(new PaymentException(ErrorCode.TOSS_API_UNAVAILABLE));
		given(dailySlotCapacityRepository.findBySlotIdAndDate(SLOT_ID, VISIT_DATE)).willReturn(Optional.of(capacity));

		scheduler.expirePendingReservations();

		assertThat(pendingReservation.getStatus()).isEqualTo(ReservationStatus.PAYMENT_FAILED);
		verify(reservationRepository).updateStatus(pendingReservation);
		verify(paymentQueuePublisher, never()).publish(any());
	}

	@Test
	@DisplayName("만료 처리 - 좌석이 DB와 Redis에 복원된다")
	void expirePendingReservations_fail_restoresSeatCapacity() {
		given(reservationRepository.findPendingBefore(any())).willReturn(List.of(pendingReservation));
		given(paymentClient.queryByPaymentKey(PAYMENT_KEY)).willThrow(new PaymentException(ErrorCode.TOSS_API_UNAVAILABLE));
		given(dailySlotCapacityRepository.findBySlotIdAndDate(SLOT_ID, VISIT_DATE)).willReturn(Optional.of(capacity));

		scheduler.expirePendingReservations();

		assertThat(capacity.getRemainingCount()).isEqualTo(INITIAL_REMAINING + PARTY_SIZE);
		verify(dailySlotCapacityRepository).updateRemainingCount(capacity);
		verify(reservationPublisher).releaseSeat(SLOT_ID, VISIT_DATE, PARTY_SIZE);
	}

	@Test
	@DisplayName("만료 처리 - DailySlotCapacity가 없어도 예약 상태는 PAYMENT_FAILED로 변경된다")
	void expirePendingReservations_capacityNotFound_stillFailsReservation() {
		given(reservationRepository.findPendingBefore(any())).willReturn(List.of(pendingReservation));
		given(paymentClient.queryByPaymentKey(PAYMENT_KEY)).willThrow(new PaymentException(ErrorCode.TOSS_API_UNAVAILABLE));
		given(dailySlotCapacityRepository.findBySlotIdAndDate(SLOT_ID, VISIT_DATE)).willReturn(Optional.empty());

		scheduler.expirePendingReservations();

		assertThat(pendingReservation.getStatus()).isEqualTo(ReservationStatus.PAYMENT_FAILED);
		verify(reservationRepository).updateStatus(pendingReservation);
		verify(dailySlotCapacityRepository, never()).updateRemainingCount(any());
	}

	@Test
	@DisplayName("만료 처리 - 여러 건이 모두 처리된다")
	void expirePendingReservations_multipleExpired_processesAll() {
		Reservation reservation1 = ReservationFixture.pending()
				.reservationId(1L).slotId(SLOT_ID).partySize(2).visitAt(VISIT_DATE.atTime(18, 0)).build();
		Reservation reservation2 = ReservationFixture.pending()
				.reservationId(2L).slotId(SLOT_ID).partySize(1).visitAt(VISIT_DATE.atTime(19, 0)).build();
		Reservation reservation3 = ReservationFixture.pending()
				.reservationId(3L).slotId(SLOT_ID).partySize(3).visitAt(VISIT_DATE.atTime(20, 0)).build();

		given(reservationRepository.findPendingBefore(any())).willReturn(List.of(reservation1, reservation2, reservation3));
		given(dailySlotCapacityRepository.findBySlotIdAndDate(eq(SLOT_ID), any())).willReturn(Optional.of(capacity));

		scheduler.expirePendingReservations();

		assertThat(reservation1.getStatus()).isEqualTo(ReservationStatus.PAYMENT_FAILED);
		assertThat(reservation2.getStatus()).isEqualTo(ReservationStatus.PAYMENT_FAILED);
		assertThat(reservation3.getStatus()).isEqualTo(ReservationStatus.PAYMENT_FAILED);
		verify(reservationRepository, times(3)).updateStatus(any());
		verify(reservationPublisher, times(3)).releaseSeat(eq(SLOT_ID), any(), anyInt());
		verifyNoInteractions(paymentClient);
	}

	@Test
	@DisplayName("만료 기준 시각이 현재로부터 정확히 10분(600초) 이전으로 계산된다")
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
