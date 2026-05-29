package com.reservation.tablereservationservice.presentation.restaurant.dto;

import java.time.LocalTime;

import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;

import lombok.Builder;
import lombok.Getter;

@Getter
public class RestaurantSlotResponseDto {

	private Long slotId;
	private LocalTime time;
	private int remainingCount;
	private int depositPerPerson;

	@Builder
	public RestaurantSlotResponseDto(Long slotId, LocalTime time, int remainingCount, int depositPerPerson) {
		this.slotId = slotId;
		this.time = time;
		this.remainingCount = remainingCount;
		this.depositPerPerson = depositPerPerson;
	}

	public static RestaurantSlotResponseDto of(RestaurantSlot slot, int remainingCount) {
		return RestaurantSlotResponseDto.builder()
				.slotId(slot.getSlotId())
				.time(slot.getTime())
				.remainingCount(remainingCount)
				.depositPerPerson(slot.getDepositPerPerson())
				.build();
	}
}
