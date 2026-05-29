package com.reservation.tablereservationservice.application.reservation.facade;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.fixture.ReservationFixture;
import com.reservation.tablereservationservice.fixture.UserFixture;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.CancelQueuePublisher;
import com.reservation.tablereservationservice.infrastructure.redis.ReservationPublisher;

@ExtendWith(MockitoExtension.class)
class ReservationCancelFacadeTest {

	private static final LocalDateTime VISIT_AT = LocalDateTime.of(2030, 6, 1, 19, 0);
	private static final Long RESERVATION_ID = 1L;

	@Mock
	private ReservationService reservationService;
	@Mock
	private ReservationPublisher reservationPublisher;
	@Mock
	private ReservationRepository reservationRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private CancelQueuePublisher cancelQueuePublisher;

	@InjectMocks
	private ReservationFacade reservationFacade;

	private User customer;

	@BeforeEach
	void setUp() {
		customer = UserFixture.customer().userId(1L).build();
		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
	}

	@Test
	@DisplayName("소유권 위반 → 403, markCancelPending 미호출")
	void cancel_forbidden_403() {
		Reservation reservation = ReservationFixture.confirmed()
				.reservationId(RESERVATION_ID)
				.userId(999L) // 다른 유저
				.visitAt(VISIT_AT)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);

		assertThatThrownBy(() -> reservationFacade.cancel(customer.getEmail(), RESERVATION_ID))
				.isInstanceOf(ReservationException.class)
				.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
						.isEqualTo(ErrorCode.RESERVATION_FORBIDDEN));

		verify(reservationService, never()).markCancelPending(anyLong());
		verify(cancelQueuePublisher, never()).publish(anyLong());
	}

	@Test
	@DisplayName("CONFIRMED → markCancelPending + MQ 발행")
	void cancel_confirmed_success() {
		Reservation reservation = ReservationFixture.confirmed()
				.reservationId(RESERVATION_ID)
				.userId(customer.getUserId())
				.visitAt(VISIT_AT)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);

		reservationFacade.cancel(customer.getEmail(), RESERVATION_ID);

		verify(reservationService).markCancelPending(RESERVATION_ID);
		verify(cancelQueuePublisher).publish(RESERVATION_ID);
	}

	@Test
	@DisplayName("CANCEL_PENDING → MQ 재발행 후 즉시 반환 (멱등), markCancelPending 미호출")
	void cancel_cancelPending_republishes_mq() {
		Reservation reservation = ReservationFixture.cancelPending()
				.reservationId(RESERVATION_ID)
				.userId(customer.getUserId())
				.visitAt(VISIT_AT)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);

		reservationFacade.cancel(customer.getEmail(), RESERVATION_ID);

		verify(cancelQueuePublisher).publish(RESERVATION_ID);
		verify(reservationService, never()).markCancelPending(anyLong());
	}

	@Test
	@DisplayName("CANCELED → 400")
	void cancel_alreadyCanceled_400() {
		Reservation reservation = ReservationFixture.canceled()
				.reservationId(RESERVATION_ID)
				.userId(customer.getUserId())
				.visitAt(VISIT_AT)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);

		assertThatThrownBy(() -> reservationFacade.cancel(customer.getEmail(), RESERVATION_ID))
				.isInstanceOf(ReservationException.class)
				.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
						.isEqualTo(ErrorCode.RESERVATION_ALREADY_CANCELED));

		verify(reservationService, never()).markCancelPending(anyLong());
	}

	@Test
	@DisplayName("PENDING 상태 → 400 취소 불가")
	void cancel_pending_notCancelable_400() {
		Reservation reservation = ReservationFixture.pending()
				.reservationId(RESERVATION_ID)
				.userId(customer.getUserId())
				.visitAt(VISIT_AT)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);

		assertThatThrownBy(() -> reservationFacade.cancel(customer.getEmail(), RESERVATION_ID))
				.isInstanceOf(ReservationException.class)
				.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
						.isEqualTo(ErrorCode.RESERVATION_NOT_CANCELABLE));

		verify(reservationService, never()).markCancelPending(anyLong());
	}

	@Test
	@DisplayName("취소 기한 초과 → 400")
	void cancel_deadlinePassed_400() {
		// visitAt = 23시간 후 → canCancelAt(now) = false
		Reservation reservation = ReservationFixture.confirmed()
				.reservationId(RESERVATION_ID)
				.userId(customer.getUserId())
				.visitAt(LocalDateTime.now().plusHours(23))
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);

		assertThatThrownBy(() -> reservationFacade.cancel(customer.getEmail(), RESERVATION_ID))
				.isInstanceOf(ReservationException.class)
				.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
						.isEqualTo(ErrorCode.RESERVATION_CANCEL_DEADLINE_PASSED));

		verify(reservationService, never()).markCancelPending(anyLong());
	}

	@Test
	@DisplayName("MQ 발행 실패 → 예외 전파 (Outbox 패턴 도입 전까지 호출자에게 위임)")
	void cancel_mqPublishFails_propagatesException() {
		Reservation reservation = ReservationFixture.confirmed()
				.reservationId(RESERVATION_ID)
				.userId(customer.getUserId())
				.visitAt(VISIT_AT)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);
		willThrow(new RuntimeException("RabbitMQ connection error")).given(cancelQueuePublisher).publish(anyLong());

		assertThatThrownBy(() -> reservationFacade.cancel(customer.getEmail(), RESERVATION_ID))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("RabbitMQ connection error");

		// DB는 이미 CANCEL_PENDING으로 전이됨 — CancelPendingRecoveryScheduler가 복구
		verify(reservationService).markCancelPending(RESERVATION_ID);
	}

	@Test
	@DisplayName("markCancelPending 동시성 충돌 → 409 전파")
	void cancel_concurrencyError_propagates() {
		Reservation reservation = ReservationFixture.confirmed()
				.reservationId(RESERVATION_ID)
				.userId(customer.getUserId())
				.visitAt(VISIT_AT)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);
		willThrow(new ReservationException(ErrorCode.RESERVATION_CONCURRENCY_ERROR))
				.given(reservationService).markCancelPending(RESERVATION_ID);

		assertThatThrownBy(() -> reservationFacade.cancel(customer.getEmail(), RESERVATION_ID))
				.isInstanceOf(ReservationException.class)
				.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
						.isEqualTo(ErrorCode.RESERVATION_CONCURRENCY_ERROR));

		verify(cancelQueuePublisher, never()).publish(anyLong());
	}
}
