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
	private Long version;

	@Builder
	public DailySlotCapacity(Long capacityId, Long slotId, LocalDate date, Integer remainingCount, Long version) {
		this.capacityId = capacityId;
		this.slotId = slotId;
		this.date = date;
		this.remainingCount = remainingCount;
		this.version = version;
	}

	public boolean hasEnough(int partySize) {
		return partySize > 0 && remainingCount >= partySize;
	}

	public boolean decrease(int partySize) {
		if (!hasEnough(partySize)) {
			return false;
		}
		this.remainingCount -= partySize;
		return true;
	}

	public void increase(int partySize) {
		this.remainingCount += partySize;
	}

}
