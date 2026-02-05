package com.reservation.tablereservationservice.infrastructure.notification.entity;

import java.time.LocalDateTime;

import com.reservation.tablereservationservice.domain.notification.NotificationType;
import com.reservation.tablereservationservice.infrastructure.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long notificationId;

	@Column(nullable = false)
	private Long receiverId;

	@Column(nullable = false)
	private Long reservationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationType type;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 500)
	private String content;

	private LocalDateTime readAt;

	@Builder
	public NotificationEntity(Long receiverId, Long reservationId, NotificationType type,
		String title, String content, LocalDateTime readAt) {
		this.receiverId = receiverId;
		this.reservationId = reservationId;
		this.type = type;
		this.title = title;
		this.content = content;
		this.readAt = readAt;
	}
}
