package com.reservation.tablereservationservice.application.reservation.scheduler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.fixture.ReservationFixture;
import com.reservation.tablereservationservice.global.config.CancelQueueProperties;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.CancelQueuePublisher;

@ExtendWith(MockitoExtension.class)
class CancelPendingRecoverySchedulerTest {

	private static final int THRESHOLD_MINUTES = 10;

	@Mock
	private ReservationRepository reservationRepository;
	@Mock
	private CancelQueuePublisher cancelQueuePublisher;
	@Mock
	private CancelQueueProperties cancelQueueProperties;

	@InjectMocks
	private CancelPendingRecoveryScheduler scheduler;

	@BeforeEach
	void setUp() {
		given(cancelQueueProperties.getRecoveryThresholdMinutes()).willReturn(THRESHOLD_MINUTES);
	}

	@Test
	@DisplayName("복구 대상 없음 - publish 호출 없이 종료")
	void recoverCancelPending_noStale_doesNotPublish() {
		given(reservationRepository.findCancelPendingBefore(any(LocalDateTime.class))).willReturn(List.of());

		scheduler.recoverCancelPending();

		verifyNoInteractions(cancelQueuePublisher);
	}

	@Test
	@DisplayName("복구 대상 여러 건 - 각 reservationId에 publish 호출")
	void recoverCancelPending_multipleStale_publishesAll() {
		Reservation r1 = ReservationFixture.cancelPending().reservationId(1L).build();
		Reservation r2 = ReservationFixture.cancelPending().reservationId(2L).build();
		Reservation r3 = ReservationFixture.cancelPending().reservationId(3L).build();

		given(reservationRepository.findCancelPendingBefore(any())).willReturn(List.of(r1, r2, r3));

		scheduler.recoverCancelPending();

		verify(cancelQueuePublisher, times(3)).publish(anyLong());
		verify(cancelQueuePublisher).publish(1L);
		verify(cancelQueuePublisher).publish(2L);
		verify(cancelQueuePublisher).publish(3L);
	}

	@Test
	@DisplayName("첫 번째 publish 실패 - 예외를 삼키고 나머지 건도 처리")
	void recoverCancelPending_publishFails_continuesNext() {
		Reservation r1 = ReservationFixture.cancelPending().reservationId(1L).build();
		Reservation r2 = ReservationFixture.cancelPending().reservationId(2L).build();

		given(reservationRepository.findCancelPendingBefore(any())).willReturn(List.of(r1, r2));
		willThrow(new RuntimeException("RabbitMQ unavailable")).given(cancelQueuePublisher).publish(1L);

		assertThatCode(() -> scheduler.recoverCancelPending()).doesNotThrowAnyException();

		verify(cancelQueuePublisher).publish(1L);
		verify(cancelQueuePublisher).publish(2L);
	}

	@Test
	@DisplayName("조회 기준 시각 - 현재로부터 정확히 10분 이전으로 계산된다")
	void recoverCancelPending_queryThreshold_isBeforeThreshold() {
		given(reservationRepository.findCancelPendingBefore(any())).willReturn(List.of());

		LocalDateTime before = LocalDateTime.now().minusMinutes(THRESHOLD_MINUTES);

		scheduler.recoverCancelPending();

		ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(reservationRepository).findCancelPendingBefore(captor.capture());
		assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusMinutes(THRESHOLD_MINUTES - 1));
		assertThat(captor.getValue()).isAfterOrEqualTo(before.minusSeconds(1));
	}
}
