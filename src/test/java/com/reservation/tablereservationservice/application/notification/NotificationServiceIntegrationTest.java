package com.reservation.tablereservationservice.application.notification;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.domain.notification.AlarmMessage;
import com.reservation.tablereservationservice.domain.notification.NotificationType;
import com.reservation.tablereservationservice.infrastructure.notification.entity.NotificationEntity;
import com.reservation.tablereservationservice.infrastructure.notification.repository.NotificationEntityRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceIntegrationTest {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationEntityRepository notificationEntityRepository;

	@Test
	@DisplayName("알림 저장 성공 - notificationId 반환 및 DB 저장 확인")
	void save_success() {
		// given
		AlarmMessage message = AlarmMessage.builder()
			.receiverId(1L)
			.reservationId(10L)
			.type(NotificationType.RESERVATION_CONFIRMED)
			.title("예약이 완료됐어요!")
			.content("강남 한상 / 2026-03-01 19:00 / 2명")
			.build();

		// when
		Long notificationId = notificationService.save(message);

		// then
		assertThat(notificationId).isNotNull();

		NotificationEntity saved = notificationEntityRepository.findById(notificationId)
			.orElseThrow();

		assertThat(saved.getReceiverId()).isEqualTo(1L);
		assertThat(saved.getReservationId()).isEqualTo(10L);
		assertThat(saved.getType()).isEqualTo(NotificationType.RESERVATION_CONFIRMED);
		assertThat(saved.getTitle()).isEqualTo("예약이 완료됐어요!");
		assertThat(saved.getContent()).isEqualTo("강남 한상 / 2026-03-01 19:00 / 2명");
		assertThat(saved.getReadAt()).isNull();
	}
}
