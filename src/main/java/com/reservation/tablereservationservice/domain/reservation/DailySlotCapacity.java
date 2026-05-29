package com.reservation.tablereservationservice.domain.reservation;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
public class DailySlotCapacity {

	private Long capacityId;
	private Long slotId;
	private LocalDate date;
	private Integer remainingCount;

	@Builder
	public DailySlotCapacity(Long capacityId, Long slotId, LocalDate date, Integer remainingCount) {
		this.capacityId = capacityId;
		this.slotId = slotId;
		this.date = date;
		this.remainingCount = remainingCount;
	}
}
