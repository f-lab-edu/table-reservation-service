package com.reservation.tablereservationservice.fixture;

import java.time.LocalDate;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DailySlotCapacityFixture {

	private Long capacityId = 777L;
	private Long slotId = 10L;
	private LocalDate date = LocalDate.of(2026, 1, 26);
	private int remainingCount = 10;
	private Long version = 0L;

	public static DailySlotCapacityFixture capacity() {
		return new DailySlotCapacityFixture();
	}

	public DailySlotCapacity build() {
		return DailySlotCapacity.builder()
			.capacityId(capacityId)
			.slotId(slotId)
			.date(date)
			.remainingCount(remainingCount)
			.version(version)
			.build();
	}
}
