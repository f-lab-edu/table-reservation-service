package com.reservation.tablereservationservice.presentation.notification.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.reservation.tablereservationservice.application.notification.NotificationService;
import com.reservation.tablereservationservice.global.annotation.LoginUser;
import com.reservation.tablereservationservice.global.common.CurrentUser;
import com.reservation.tablereservationservice.presentation.common.ApiResponse;
import com.reservation.tablereservationservice.presentation.common.PageResponseDto;
import com.reservation.tablereservationservice.presentation.notification.dto.NotificationResponseDto;

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

	@GetMapping
	public ApiResponse<PageResponseDto<NotificationResponseDto>> getNotifications(
			@LoginUser CurrentUser user,
			@PageableDefault(size = 20, sort = "createdAt") Pageable pageable
	) {
		return ApiResponse.success("알림 목록 조회 성공", notificationService.findAll(user.userId(), pageable));
	}

}
