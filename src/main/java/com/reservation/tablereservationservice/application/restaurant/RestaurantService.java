package com.reservation.tablereservationservice.application.restaurant;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.presentation.restaurant.dto.RestaurantCursorResponseDto;
import com.reservation.tablereservationservice.presentation.restaurant.dto.RestaurantResponseDto;
import com.reservation.tablereservationservice.presentation.restaurant.dto.RestaurantSearchDto;
import com.reservation.tablereservationservice.presentation.restaurant.dto.RestaurantSlotResponseDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RestaurantService {

	private final RestaurantRepository restaurantRepository;
	private final RestaurantSlotRepository restaurantSlotRepository;
	private final DailySlotCapacityRepository dailySlotCapacityRepository;

	@Transactional(readOnly = true)
	public RestaurantCursorResponseDto findRestaurants(RestaurantSearchDto searchDto) {
		int size = searchDto.getSize();
		List<Restaurant> results = restaurantRepository.findByFilterWithCursor(
				searchDto.getRegionCode(), searchDto.getCategoryCode(), searchDto.getCursor(), size + 1);

		boolean hasNext = results.size() > size;
		List<Restaurant> content = hasNext ? results.subList(0, size) : results;
		Long nextCursor = hasNext ? content.get(content.size() - 1).getRestaurantId() : null;

		return RestaurantCursorResponseDto.builder()
				.content(content.stream().map(RestaurantResponseDto::from).toList())
				.nextCursor(nextCursor)
				.hasNext(hasNext)
				.build();
	}

	@Transactional(readOnly = true)
	public List<RestaurantSlotResponseDto> findAvailableSlots(Long restaurantId, LocalDate date) {
		List<RestaurantSlot> slots = restaurantSlotRepository.findAllByRestaurantId(restaurantId);
		if (slots.isEmpty()) {
			return List.of();
		}

		List<Long> slotIds = slots.stream().map(RestaurantSlot::getSlotId).toList();
		Map<Long, Integer> capacityBySlotId = dailySlotCapacityRepository
				.findAllBySlotIdsAndDate(slotIds, date)
				.stream()
				.collect(Collectors.toMap(DailySlotCapacity::getSlotId, DailySlotCapacity::getRemainingCount));

		return slots.stream()
				.filter(slot -> capacityBySlotId.containsKey(slot.getSlotId()))
				.map(slot -> RestaurantSlotResponseDto.of(slot, capacityBySlotId.get(slot.getSlotId())))
				.toList();
	}
}
