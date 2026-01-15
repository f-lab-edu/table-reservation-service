package com.reservation.tablereservationservice.presentation.common.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reservation.tablereservationservice.presentation.common.ApiResponse;

@RestController
@RequestMapping("/api/health")
public class HealthController {

	@GetMapping
	public ApiResponse<String> healthCheck() {
		return ApiResponse.success("서비스가 정상적으로 작동 중입니다.", "OK");
	}
}
