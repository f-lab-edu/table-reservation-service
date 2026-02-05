package com.reservation.tablereservationservice.presentation.notification.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.reservation.tablereservationservice.application.notification.NotificationService;
import com.reservation.tablereservationservice.global.annotation.LoginUser;
import com.reservation.tablereservationservice.global.common.CurrentUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter subscribe(@LoginUser CurrentUser user) {
		return notificationService.subscribe(user.userId());
	}
}
