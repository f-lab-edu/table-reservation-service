package com.reservation.tablereservationservice.application.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reservation.tablereservationservice.domain.notification.AlarmMessage;
import com.reservation.tablereservationservice.domain.notification.Notification;
import com.reservation.tablereservationservice.domain.notification.NotificationRepository;
import com.reservation.tablereservationservice.domain.notification.NotificationType;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@InjectMocks
	private NotificationService notificationService;

	@Test
	@DisplayName("알림을 저장하고, 생성된 notificationId를 반환한다.")
	void save_success() {
		// given
		AlarmMessage message = AlarmMessage.builder()
			.receiverId(1L)
			.reservationId(10L)
			.type(NotificationType.RESERVATION_CONFIRMED)
			.title("예약이 완료됐어요!")
			.content("강남 한상 / 2026-03-01 19:00 / 2명")
			.build();

		given(notificationRepository.save(any(Notification.class)))
			.willReturn(Notification.builder().notificationId(100L).build());

		ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

		// when
		Long notificationId = notificationService.save(message);

		// then
		assertThat(notificationId).isEqualTo(100L);
		verify(notificationRepository).save(captor.capture());
		Notification toSave = captor.getValue();
		assertThat(toSave.getNotificationId()).isNull();
		assertThat(toSave.getReceiverId()).isEqualTo(1L);
		assertThat(toSave.getReservationId()).isEqualTo(10L);
	}
}
