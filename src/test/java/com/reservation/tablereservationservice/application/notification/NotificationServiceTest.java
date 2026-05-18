package com.reservation.tablereservationservice.application.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reservation.tablereservationservice.domain.notification.Notification;
import com.reservation.tablereservationservice.domain.notification.NotificationRepository;
import com.reservation.tablereservationservice.domain.notification.NotificationType;
import com.reservation.tablereservationservice.global.transaction.TransactionHandler;
import com.reservation.tablereservationservice.infrastructure.notification.pubsub.NotificationPublisher;
import com.reservation.tablereservationservice.infrastructure.notification.sse.SseEmitterRegistry;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock
	private SseEmitterRegistry sseEmitterRegistry;

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationPublisher pubSubPublisher;

	@Mock
	private TransactionHandler transactionHandler;

	@InjectMocks
	private NotificationService notificationService;

	@Test
	@DisplayName("예약 확정 알림 - 고객과 점주 모두 DB에 저장된다")
	void notifyReservationConfirmed_savesBothNotifications() {
		// given
		Long customerId = 1L;
		Long ownerId = 2L;
		Long reservationId = 10L;
		LocalDateTime visitAt = LocalDateTime.of(2026, 6, 1, 19, 0);
		int partySize = 2;

		ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

		// when
		notificationService.notifyReservationConfirmed(customerId, ownerId, reservationId, visitAt, partySize);

		// then: 고객 + 점주 2건 저장
		verify(notificationRepository, times(2)).save(captor.capture());
		List<Notification> saved = captor.getAllValues();

		Notification customerNotification = saved.get(0);
		assertThat(customerNotification.getReceiverId()).isEqualTo(customerId);
		assertThat(customerNotification.getReservationId()).isEqualTo(reservationId);
		assertThat(customerNotification.getType()).isEqualTo(NotificationType.RESERVATION_CONFIRMED);
		assertThat(customerNotification.isRead()).isFalse();

		Notification ownerNotification = saved.get(1);
		assertThat(ownerNotification.getReceiverId()).isEqualTo(ownerId);
		assertThat(ownerNotification.getType()).isEqualTo(NotificationType.RESERVATION_CONFIRMED);
		assertThat(ownerNotification.isRead()).isFalse();
	}

	@Test
	@DisplayName("예약 취소 알림 - 고객과 점주 모두 DB에 저장된다")
	void notifyReservationCancelled_savesBothNotifications() {
		// given
		Long customerId = 1L;
		Long ownerId = 2L;
		Long reservationId = 10L;
		LocalDateTime visitAt = LocalDateTime.of(2026, 6, 1, 19, 0);
		int partySize = 2;

		ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

		// when
		notificationService.notifyReservationCancelled(customerId, ownerId, reservationId, visitAt, partySize);

		// then: 고객 + 점주 2건 저장
		verify(notificationRepository, times(2)).save(captor.capture());
		List<Notification> saved = captor.getAllValues();

		Notification customerNotification = saved.get(0);
		assertThat(customerNotification.getReceiverId()).isEqualTo(customerId);
		assertThat(customerNotification.getType()).isEqualTo(NotificationType.RESERVATION_CANCELLED);
		assertThat(customerNotification.isRead()).isFalse();

		Notification ownerNotification = saved.get(1);
		assertThat(ownerNotification.getReceiverId()).isEqualTo(ownerId);
		assertThat(ownerNotification.getType()).isEqualTo(NotificationType.RESERVATION_CANCELLED);
		assertThat(ownerNotification.isRead()).isFalse();
	}

	@Test
	@DisplayName("알림 저장 후 afterCommit 콜백으로 Pub/Sub 발행이 예약된다")
	void notifyReservationConfirmed_schedulesPublishAfterCommit() {
		// given
		LocalDateTime visitAt = LocalDateTime.of(2026, 6, 1, 19, 0);

		// when
		notificationService.notifyReservationConfirmed(1L, 2L, 10L, visitAt, 2);

		// then: 고객 + 점주 각각 afterCommit 콜백 등록, publish는 즉시 호출되지 않음
		verify(transactionHandler, times(2)).runAfterCommit(any());
		verify(pubSubPublisher, never()).publish(any());
	}
}