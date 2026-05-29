package com.reservation.tablereservationservice.presentation.restaurant.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.tablereservationservice.application.restaurant.RestaurantService;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.fixture.RestaurantFixture;
import com.reservation.tablereservationservice.global.exception.GlobalExceptionHandler;
import com.reservation.tablereservationservice.presentation.common.ApiResponse;
import com.reservation.tablereservationservice.presentation.restaurant.dto.RestaurantCursorResponseDto;
import com.reservation.tablereservationservice.presentation.restaurant.dto.RestaurantResponseDto;

@WebMvcTest(RestaurantController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class RestaurantControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private RestaurantService restaurantService;
	@MockitoBean
	private UserRepository userRepository;

	@Test
	@DisplayName("매장 목록 조회 - 파라미터 없이 200 반환")
	void findRestaurants_noParams_returns200() throws Exception {
		RestaurantCursorResponseDto response = RestaurantCursorResponseDto.builder()
				.content(List.of(RestaurantResponseDto.from(RestaurantFixture.restaurant().restaurantId(1L).build())))
				.nextCursor(null)
				.hasNext(false)
				.build();

		given(restaurantService.findRestaurants(any())).willReturn(response);

		MvcResult result = mockMvc.perform(get("/api/restaurants"))
				.andExpect(status().isOk())
				.andReturn();

		ApiResponse<RestaurantCursorResponseDto> apiResponse = objectMapper.readValue(
				result.getResponse().getContentAsString(),
				new TypeReference<>() {
				}
		);

		assertThat(apiResponse.getCode()).isEqualTo(200);
		assertThat(apiResponse.getMessage()).isEqualTo("매장 목록 조회 성공");
		assertThat(apiResponse.getData().getContent()).hasSize(1);
	}

	@Test
	@DisplayName("매장 목록 조회 - 지역/카테고리/cursor 파라미터가 SearchDto로 바인딩된다")
	void findRestaurants_params_bindToSearchDto() throws Exception {
		given(restaurantService.findRestaurants(any()))
				.willReturn(RestaurantCursorResponseDto.builder().content(List.of()).hasNext(false).build());

		mockMvc.perform(get("/api/restaurants")
						.param("regionCode", "RG01")
						.param("categoryCode", "CT01")
						.param("cursor", "5")
						.param("size", "20"))
				.andExpect(status().isOk());

		verify(restaurantService).findRestaurants(argThat(dto ->
				dto.getRegionCode().name().equals("RG01") &&
						dto.getCategoryCode().name().equals("CT01") &&
						dto.getCursor().equals(5L) &&
						dto.getSize() == 20
		));
	}

	@Test
	@DisplayName("슬롯 조회 - 200 + 슬롯 목록 반환")
	void findAvailableSlots_returns200() throws Exception {
		given(restaurantService.findAvailableSlots(eq(1L), any(LocalDate.class)))
				.willReturn(List.of());

		mockMvc.perform(get("/api/restaurants/1/slots").param("date", "2030-01-01"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("슬롯 조회 - date 파라미터 없으면 400")
	void findAvailableSlots_missingDate_returns400() throws Exception {
		mockMvc.perform(get("/api/restaurants/1/slots"))
				.andExpect(status().isBadRequest());
	}
}
