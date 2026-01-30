package com.reservation.tablereservationservice.presentation.reservation.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.GlobalExceptionHandler;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.presentation.common.ApiResponse;
import com.reservation.tablereservationservice.presentation.common.PageResponseDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto;
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
			List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
		);

		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/reservations")
				.with(authentication(auth))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andExpect(status().isOk())
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

		verify(reservationService).create(eq(email), any(ReservationRequestDto.class));
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
			List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
		);

		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/reservations")
				.with(authentication(auth))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andExpect(status().isBadRequest())
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

		verify(reservationService, never()).create(anyString(), any());
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
			List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
		);

		// when
		mockMvc.perform(post("/api/reservations")
				.with(authentication(auth))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andExpect(status().isForbidden());

		// then
		verify(reservationService, never()).create(anyString(), any());
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
			List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
		);

		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/reservations")
				.with(authentication(auth))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andExpect(status().isConflict())
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

		verify(reservationService).create(eq(email), any(ReservationRequestDto.class));
	}

	@Test
	@DisplayName("내 예약 목록 조회 성공 - 200 및 응답 바디 반환")
	void getReservations_me_success_whenCustomerRole() throws Exception {
		// given
		String email = "customer01@test.com";

		ReservationListResponseDto content = ReservationListResponseDto.builder()
			.reservationId(1L)
			.restaurantId(1L)
			.restaurantName("테스트 식당")
			.visitAt(LocalDateTime.of(2026, 1, 27, 12, 0))
			.partySize(2)
			.status(ReservationStatus.CONFIRMED)
			.build();

		Pageable pageable = PageRequest.of(0, 10);
		Page<ReservationListResponseDto> page = new PageImpl<>(List.of(content), pageable, 1);
		PageResponseDto<ReservationListResponseDto> responseDto = PageResponseDto.from(page);

		given(reservationService.findMyReservations(
			eq(email),
			any(),
			any(),
			any(),
			any(Pageable.class)
		)).willReturn(responseDto);

		Authentication auth = new UsernamePasswordAuthenticationToken(
			email,
			null,
			List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
		);

		// when
		MvcResult mvcResult = mockMvc.perform(get("/api/reservations/me")
				.with(authentication(auth))
				.param("fromDate", "2026-01-27")
				.param("toDate", "2026-02-27")
				.param("status", "CONFIRMED"))
			.andExpect(status().isOk())
			.andReturn();

		// then
		ApiResponse<Object> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<Object>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getMessage()).isEqualTo("예약 조회 성공");
		assertThat(response.getData()).isNotNull();

		verify(reservationService).findMyReservations(
			eq(email),
			any(),
			any(),
			any(),
			any(Pageable.class)
		);
	}

	@Test
	@DisplayName("점주 예약 목록 조회 성공 - 200 및 응답 바디 반환")
	void getReservations_owner_success_whenOwnerRole() throws Exception {
		// given
		String email = "owner@test.com";

		ReservationListResponseDto content = ReservationListResponseDto.builder()
			.reservationId(1L)
			.restaurantId(1L)
			.restaurantName("테스트 식당")
			.visitAt(LocalDateTime.of(2026, 1, 27, 12, 0))
			.partySize(2)
			.status(ReservationStatus.CONFIRMED)
			.build();

		Pageable pageable = PageRequest.of(0, 10);
		Page<ReservationListResponseDto> page = new PageImpl<>(List.of(content), pageable, 1);
		PageResponseDto<ReservationListResponseDto> responseDto = PageResponseDto.from(page);

		given(reservationService.findOwnerReservations(
			eq(email),
			any(),
			any(),
			any(),
			any(Pageable.class)
		)).willReturn(responseDto);

		Authentication auth = new UsernamePasswordAuthenticationToken(
			email,
			null,
			List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
		);

		// when
		MvcResult mvcResult = mockMvc.perform(get("/api/reservations/owner")
				.with(authentication(auth))
				.param("fromDate", "2026-01-27")
				.param("toDate", "2026-02-27"))
			.andExpect(status().isOk())
			.andReturn();

		// then
		ApiResponse<Object> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<Object>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getMessage()).isEqualTo("예약 조회 성공");
		assertThat(response.getData()).isNotNull();

		verify(reservationService).findOwnerReservations(
			eq(email),
			any(),
			any(),
			any(),
			any(Pageable.class)
		);
	}

	private <T> ApiResponse<T> readResponse(
		MvcResult mvcResult,
		TypeReference<ApiResponse<T>> typeRef
	) throws Exception {
		String body = mvcResult.getResponse().getContentAsString();
		return objectMapper.readValue(body, typeRef);
	}

	@TestConfiguration
	@EnableMethodSecurity
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
