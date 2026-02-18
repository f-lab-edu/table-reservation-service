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

	private Long capacityId = null;
	private Long slotId = 10L;
	private LocalDate date = LocalDate.of(2030, 1, 1);
	private int remainingCount = 10;
	private Long version = 0L;

	public static DailySlotCapacityFixture capacity() {
		return new DailySlotCapacityFixture();
	}

	public DailySlotCapacity build() {
		DailySlotCapacity.DailySlotCapacityBuilder builder = DailySlotCapacity.builder()
			.slotId(slotId)
			.date(date)
			.remainingCount(remainingCount)
			.version(version);

		if (capacityId != null) {
			builder.capacityId(capacityId);
		}

		return builder.build();
	}
}
