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
	private Integer maxCount;
	private long version;

	@Builder
	public DailySlotCapacity(Long capacityId, Long slotId, LocalDate date, Integer remainingCount, Integer maxCount,
		long version) {
		this.capacityId = capacityId;
		this.slotId = slotId;
		this.date = date;
		this.remainingCount = remainingCount;
		this.maxCount = maxCount;
		this.version = version;
	}

	public void decrease(int count) {
		this.remainingCount -= count;
	}

	public boolean hasEnough(int partySize) {
		if (partySize <= 0) {
			return false;
		}

		if (partySize > maxCount) {
			return false;
		}
		return remainingCount >= partySize;
	}

}
