package com.reservation.tablereservationservice.presentation.reservation.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.global.config.SecurityConfig;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.GlobalExceptionHandler;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.presentation.common.ApiResponse;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationResponseDto;

@WebMvcTest(ReservationController.class)
@Import({GlobalExceptionHandler.class, ReservationControllerTest.TestSecurityConfig.class})
@AutoConfigureMockMvc
class ReservationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ReservationService reservationService;

	@MockitoBean
	private Authentication authentication;

	@Test
	@DisplayName("예약 요청 성공 - CUSTOMER 권한이면 200 및 응답 바디 반환")
	void create_success_whenCustomerRole() throws Exception {
		// given
		String email = "customer01@test.com";
		LocalDate date = LocalDate.of(2026, 1, 26);

		ReservationRequestDto requestDto = new ReservationRequestDto(
			10L,
			date,
			2,
			"note"
		);

		Reservation reservation = Reservation.builder()
			.reservationId(999L)
			.userId(1L)
			.slotId(10L)
			.visitAt(LocalDateTime.of(2026, 1, 26, 19, 0))
			.partySize(2)
			.note("note")
			.status(ReservationStatus.CONFIRMED)
			.build();

		given(reservationService.create(eq(email), any(ReservationRequestDto.class)))
			.willReturn(reservation);

		Authentication auth = new UsernamePasswordAuthenticationToken(
			email,
			null,
			java.util.List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
		);

		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/reservations")
				.with(authentication(auth))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andReturn();

		// then
		ApiResponse<ReservationResponseDto> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<ReservationResponseDto>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getMessage()).isEqualTo("예약 요청 성공");

		ReservationResponseDto data = response.getData();
		assertThat(data).isNotNull();
		assertThat(data.getReservationId()).isEqualTo(999L);
		assertThat(data.getPartySize()).isEqualTo(2);

		verify(reservationService, times(1)).create(eq(email), any(ReservationRequestDto.class));
	}

	@Test
	@DisplayName("예약 요청 실패 - 요청 DTO 검증 실패 시 400 + 상세 에러 메시지 반환")
	void create_InvalidRequestDto_ReturnsBadRequest() throws Exception {
		// given
		String email = "customer01@test.com";

		ReservationRequestDto requestDto = new ReservationRequestDto(
			null,
			null,
			0,
			"note"
		);

		Authentication auth = new UsernamePasswordAuthenticationToken(
			email,
			null,
			java.util.List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
		);

		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/reservations")
				.with(authentication(auth))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andReturn();

		// then
		ApiResponse<Map<String, String>> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<Map<String, String>>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(400);
		assertThat(response.getMessage()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());

		Map<String, String> errors = response.getData();
		assertThat(errors).isNotNull();
		assertThat(errors).containsKeys("slotId", "date", "partySize");
		assertThat(errors.get("slotId")).isEqualTo("slotId는 필수입니다.");
		assertThat(errors.get("date")).isEqualTo("예약 날짜는 필수입니다.");
		assertThat(errors.get("partySize")).isEqualTo("예약 인원은 1명 이상이어야 합니다.");

		verify(reservationService, times(0)).create(anyString(), any());
	}

	@Test
	@DisplayName("예약 요청 실패 - CUSTOMER 권한이 아니면 403")
	void create_Fail_NotCustomerRole_ReturnsForbidden() throws Exception {
		// given
		String email = "owner@test.com";
		ReservationRequestDto requestDto = new ReservationRequestDto(
			10L,
			LocalDate.of(2026, 1, 26),
			2,
			"note"
		);

		Authentication auth = new UsernamePasswordAuthenticationToken(
			email,
			null,
			java.util.List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
		);

		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/reservations")
				.with(authentication(auth))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andReturn();

		// then
		assertThat(mvcResult.getResponse().getStatus()).isEqualTo(403);
		verify(reservationService, times(0)).create(anyString(), any());
	}

	@Test
	@DisplayName("예약 요청 실패 - 중복 예약 예외 발생 시 409 응답")
	void create_Fail_DuplicatedTime_ReturnsConflict() throws Exception {
		// given
		String email = "customer01@test.com";
		ReservationRequestDto requestDto = new ReservationRequestDto(
			10L,
			LocalDate.of(2026, 1, 26),
			2,
			""
		);

		given(reservationService.create(eq(email), any(ReservationRequestDto.class)))
			.willThrow(new ReservationException(ErrorCode.RESERVATION_DUPLICATED_TIME));

		Authentication auth = new UsernamePasswordAuthenticationToken(
			email,
			null,
			java.util.List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
		);
		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/reservations")
				.with(authentication(auth))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andReturn();

		// then
		ApiResponse<Void> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<Void>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(409);
		assertThat(response.getMessage()).isEqualTo(ErrorCode.RESERVATION_DUPLICATED_TIME.getMessage());
		assertThat(response.getData()).isNull();

		verify(reservationService, times(1)).create(eq(email), any(ReservationRequestDto.class));
	}

	private <T> ApiResponse<T> readResponse(
		MvcResult mvcResult,
		TypeReference<ApiResponse<T>> typeRef
	) throws Exception {
		String body = mvcResult.getResponse().getContentAsString();
		return objectMapper.readValue(body, typeRef);
	}

	@TestConfiguration
	@EnableMethodSecurity(prePostEnabled = true)
	static class TestSecurityConfig {

	    @Bean
		SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
	        // WebMvcTest에서 403/401/CSRF 흐름만 확인하려면 최소 설정으로 둠
	        return http
	            .csrf(csrf -> csrf.disable())
	            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
	            .build();
	    }
	}
}
