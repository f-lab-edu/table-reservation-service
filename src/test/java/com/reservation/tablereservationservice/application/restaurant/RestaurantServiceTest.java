package com.reservation.tablereservationservice.application.restaurant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.restaurant.CategoryCode;
import com.reservation.tablereservationservice.domain.restaurant.RegionCode;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.fixture.RestaurantFixture;
import com.reservation.tablereservationservice.fixture.RestaurantSlotFixture;
import com.reservation.tablereservationservice.presentation.restaurant.dto.RestaurantCursorResponseDto;
import com.reservation.tablereservationservice.presentation.restaurant.dto.RestaurantSearchDto;
import com.reservation.tablereservationservice.presentation.restaurant.dto.RestaurantSlotResponseDto;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

	@Mock
	private RestaurantRepository restaurantRepository;
	@Mock
	private RestaurantSlotRepository restaurantSlotRepository;
	@Mock
	private DailySlotCapacityRepository dailySlotCapacityRepository;

	@InjectMocks
	private RestaurantService restaurantService;

	private Restaurant restaurant1;
	private Restaurant restaurant2;
	private Restaurant restaurant3;

	@BeforeEach
	void setUp() {
		restaurant1 = RestaurantFixture.restaurant().restaurantId(1L).build();
		restaurant2 = RestaurantFixture.restaurant().restaurantId(2L).build();
		restaurant3 = RestaurantFixture.restaurant().restaurantId(3L).build();
	}

	private RestaurantSearchDto searchDto(RegionCode regionCode, CategoryCode categoryCode, Long cursor, int size) {
		return RestaurantSearchDto.builder()
				.regionCode(regionCode)
				.categoryCode(categoryCode)
				.cursor(cursor)
				.size(size)
				.build();
	}

	@Test
	@DisplayName("매장 목록 조회 - 필터 없이 전체 조회")
	void findRestaurants_noFilter_returnsAll() {
		given(restaurantRepository.findByFilterWithCursor(null, null, null, 11))
				.willReturn(List.of(restaurant1, restaurant2, restaurant3));

		RestaurantCursorResponseDto result = restaurantService.findRestaurants(searchDto(null, null, null, 10));

		assertThat(result.getContent()).hasSize(3);
		assertThat(result.isHasNext()).isFalse();
		assertThat(result.getNextCursor()).isNull();
	}

	@Test
	@DisplayName("매장 목록 조회 - 지역 필터 적용")
	void findRestaurants_withRegionFilter() {
		given(restaurantRepository.findByFilterWithCursor(RegionCode.RG01, null, null, 11))
				.willReturn(List.of(restaurant1));

		RestaurantCursorResponseDto result = restaurantService.findRestaurants(
				searchDto(RegionCode.RG01, null, null, 10));

		assertThat(result.getContent()).hasSize(1);
	}

	@Test
	@DisplayName("매장 목록 조회 - 카테고리 필터 적용")
	void findRestaurants_withCategoryFilter() {
		given(restaurantRepository.findByFilterWithCursor(null, CategoryCode.CT01, null, 11))
				.willReturn(List.of(restaurant1, restaurant2));

		RestaurantCursorResponseDto result = restaurantService.findRestaurants(
				searchDto(null, CategoryCode.CT01, null, 10));

		assertThat(result.getContent()).hasSize(2);
	}

	@Test
	@DisplayName("매장 목록 조회 - hasNext true, nextCursor 반환")
	void findRestaurants_hasNextTrue_returnsNextCursor() {
		given(restaurantRepository.findByFilterWithCursor(null, null, null, 3))
				.willReturn(List.of(restaurant1, restaurant2, restaurant3));

		RestaurantCursorResponseDto result = restaurantService.findRestaurants(
				searchDto(null, null, null, 2));

		assertThat(result.getContent()).hasSize(2);
		assertThat(result.isHasNext()).isTrue();
		assertThat(result.getNextCursor()).isEqualTo(restaurant2.getRestaurantId());
	}

	@Test
	@DisplayName("매장 목록 조회 - cursor 적용 시 해당 ID 이후 조회")
	void findRestaurants_withCursor_queriesAfterCursor() {
		given(restaurantRepository.findByFilterWithCursor(null, null, 1L, 11))
				.willReturn(List.of(restaurant2, restaurant3));

		RestaurantCursorResponseDto result = restaurantService.findRestaurants(
				searchDto(null, null, 1L, 10));

		assertThat(result.getContent()).hasSize(2);
		assertThat(result.getContent().get(0).getRestaurantId()).isEqualTo(restaurant2.getRestaurantId());
	}

	@Test
	@DisplayName("슬롯 조회 - 오픈된 슬롯만 반환")
	void findAvailableSlots_returnsOnlyOpenedSlots() {
		LocalDate date = LocalDate.of(2030, 1, 1);
		RestaurantSlot slot1 = RestaurantSlotFixture.slot().slotId(10L).time(LocalTime.of(12, 0)).build();
		RestaurantSlot slot2 = RestaurantSlotFixture.slot().slotId(11L).time(LocalTime.of(18, 0)).build();

		given(restaurantSlotRepository.findAllByRestaurantId(1L)).willReturn(List.of(slot1, slot2));
		given(dailySlotCapacityRepository.findAllBySlotIdsAndDate(List.of(10L, 11L), date))
				.willReturn(List.of(
						DailySlotCapacity.builder().slotId(10L).date(date).remainingCount(8).build()));

		List<RestaurantSlotResponseDto> result = restaurantService.findAvailableSlots(1L, date);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getSlotId()).isEqualTo(10L);
		assertThat(result.get(0).getRemainingCount()).isEqualTo(8);
	}

	@Test
	@DisplayName("슬롯 조회 - 슬롯 없는 매장은 빈 목록 반환")
	void findAvailableSlots_noSlots_returnsEmpty() {
		given(restaurantSlotRepository.findAllByRestaurantId(1L)).willReturn(List.of());

		List<RestaurantSlotResponseDto> result = restaurantService.findAvailableSlots(1L, LocalDate.of(2030, 1, 1));

		assertThat(result).isEmpty();
		verifyNoInteractions(dailySlotCapacityRepository);
	}

	@Test
	@DisplayName("슬롯 조회 - 모든 슬롯이 미오픈인 경우 빈 목록 반환")
	void findAvailableSlots_allSlotsClosed_returnsEmpty() {
		LocalDate date = LocalDate.of(2030, 1, 1);
		RestaurantSlot slot = RestaurantSlotFixture.slot().slotId(10L).build();

		given(restaurantSlotRepository.findAllByRestaurantId(1L)).willReturn(List.of(slot));
		given(dailySlotCapacityRepository.findAllBySlotIdsAndDate(List.of(10L), date)).willReturn(List.of());

		List<RestaurantSlotResponseDto> result = restaurantService.findAvailableSlots(1L, date);

		assertThat(result).isEmpty();
	}
}
