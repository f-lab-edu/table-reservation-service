package com.reservation.tablereservationservice.application.notification;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
	@DisplayName("예약 확정 알림 - 고객과 점주 모두 DB에 저장된다")
	void notifyReservationConfirmed_persistsBothToDb() {
		// given
		Long customerId = 1L;
		Long ownerId = 2L;
		Long reservationId = 10L;
		LocalDateTime visitAt = LocalDateTime.of(2026, 6, 1, 19, 0);
		int partySize = 2;

		// when
		notificationService.notifyReservationConfirmed(customerId, ownerId, reservationId, visitAt, partySize);

		// then
		List<NotificationEntity> all = notificationEntityRepository.findAll().stream()
			.filter(e -> e.getReservationId().equals(reservationId))
			.toList();

		assertThat(all).hasSize(2);

		NotificationEntity customerNotification = all.stream()
			.filter(e -> e.getReceiverId().equals(customerId))
			.findFirst().orElseThrow();
		assertThat(customerNotification.getType()).isEqualTo(NotificationType.RESERVATION_CONFIRMED);
		assertThat(customerNotification.isRead()).isFalse();
		assertThat(customerNotification.getContent()).contains("2026-06-01").contains("2명");

		NotificationEntity ownerNotification = all.stream()
			.filter(e -> e.getReceiverId().equals(ownerId))
			.findFirst().orElseThrow();
		assertThat(ownerNotification.getType()).isEqualTo(NotificationType.RESERVATION_CONFIRMED);
		assertThat(ownerNotification.isRead()).isFalse();
		assertThat(ownerNotification.getContent()).contains("2026-06-01").contains("2명");
	}

	@Test
	@DisplayName("예약 취소 알림 - 고객과 점주 모두 DB에 저장된다")
	void notifyReservationCancelled_persistsBothToDb() {
		// given
		Long customerId = 1L;
		Long ownerId = 2L;
		Long reservationId = 20L;
		LocalDateTime visitAt = LocalDateTime.of(2026, 6, 1, 19, 0);
		int partySize = 3;

		// when
		notificationService.notifyReservationCancelled(customerId, ownerId, reservationId, visitAt, partySize);

		// then
		List<NotificationEntity> all = notificationEntityRepository.findAll().stream()
			.filter(e -> e.getReservationId().equals(reservationId))
			.toList();

		assertThat(all).hasSize(2);

		NotificationEntity customerNotification = all.stream()
			.filter(e -> e.getReceiverId().equals(customerId))
			.findFirst().orElseThrow();
		assertThat(customerNotification.getType()).isEqualTo(NotificationType.RESERVATION_CANCELLED);
		assertThat(customerNotification.isRead()).isFalse();
		assertThat(customerNotification.getContent()).contains("2026-06-01").contains("3명");

		NotificationEntity ownerNotification = all.stream()
			.filter(e -> e.getReceiverId().equals(ownerId))
			.findFirst().orElseThrow();
		assertThat(ownerNotification.getType()).isEqualTo(NotificationType.RESERVATION_CANCELLED);
		assertThat(ownerNotification.isRead()).isFalse();
		assertThat(ownerNotification.getContent()).contains("2026-06-01").contains("3명");
	}
}