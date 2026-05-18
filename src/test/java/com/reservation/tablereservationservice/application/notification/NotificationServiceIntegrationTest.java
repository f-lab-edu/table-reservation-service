package com.reservation.tablereservationservice.application.notification;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.reservation.tablereservationservice.domain.notification.NotificationType;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.infrastructure.notification.entity.NotificationEntity;
import com.reservation.tablereservationservice.infrastructure.notification.repository.NotificationEntityRepository;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceIntegrationTest {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationEntityRepository notificationEntityRepository;

	@Test
	@DisplayName("예약 확정 알림 - 고객과 점주 모두 DB에 저장된다")
	void notifyConfirmed_persistsBothToDb() {
		// given
		Long customerId = 1L;
		Long ownerId = 2L;
		Long reservationId = 10L;
		Reservation reservation = reservation(customerId, reservationId, LocalDateTime.of(2026, 6, 1, 19, 0), 2);

		// when
		notificationService.notifyConfirmed(reservation, ownerId, "테스트식당");

		// then: @Async 실행 완료 대기
		await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
			List<NotificationEntity> all = notificationEntityRepository.findAll().stream()
					.filter(e -> e.getReservationId().equals(reservationId))
					.toList();

			assertThat(all).hasSize(2);

			NotificationEntity customerNotification = all.stream()
					.filter(e -> e.getReceiverId().equals(customerId))
					.findFirst().orElseThrow();
			assertThat(customerNotification.getType()).isEqualTo(NotificationType.CONFIRMED);
			assertThat(customerNotification.isRead()).isFalse();
			assertThat(customerNotification.getContent()).contains("6월 1일").contains("2명");

			NotificationEntity ownerNotification = all.stream()
					.filter(e -> e.getReceiverId().equals(ownerId))
					.findFirst().orElseThrow();
			assertThat(ownerNotification.getType()).isEqualTo(NotificationType.CONFIRMED);
			assertThat(ownerNotification.isRead()).isFalse();
			assertThat(ownerNotification.getContent()).contains("6월 1일").contains("2명");
		});
	}

	@Test
	@DisplayName("예약 취소 알림 - 고객과 점주 모두 DB에 저장된다")
	void notifyCanceled_persistsBothToDb() {
		// given
		Long customerId = 1L;
		Long ownerId = 2L;
		Long reservationId = 20L;
		Reservation reservation = reservation(customerId, reservationId, LocalDateTime.of(2026, 6, 1, 19, 0), 3);

		// when
		notificationService.notifyCanceled(reservation, ownerId, "테스트식당");

		// then: @Async 실행 완료 대기
		await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
			List<NotificationEntity> all = notificationEntityRepository.findAll().stream()
					.filter(e -> e.getReservationId().equals(reservationId))
					.toList();

			assertThat(all).hasSize(2);

			NotificationEntity customerNotification = all.stream()
					.filter(e -> e.getReceiverId().equals(customerId))
					.findFirst().orElseThrow();
			assertThat(customerNotification.getType()).isEqualTo(NotificationType.CANCELED);
			assertThat(customerNotification.isRead()).isFalse();
			assertThat(customerNotification.getContent()).contains("6월 1일").contains("3명");

			NotificationEntity ownerNotification = all.stream()
					.filter(e -> e.getReceiverId().equals(ownerId))
					.findFirst().orElseThrow();
			assertThat(ownerNotification.getType()).isEqualTo(NotificationType.CANCELED);
			assertThat(ownerNotification.isRead()).isFalse();
			assertThat(ownerNotification.getContent()).contains("6월 1일").contains("3명");
		});
	}

	private Reservation reservation(Long userId, Long reservationId, LocalDateTime visitAt, int partySize) {
		return Reservation.builder()
				.userId(userId)
				.reservationId(reservationId)
				.slotId(1L)
				.visitAt(visitAt)
				.partySize(partySize)
				.status(ReservationStatus.CONFIRMED)
				.build();
	}
}
