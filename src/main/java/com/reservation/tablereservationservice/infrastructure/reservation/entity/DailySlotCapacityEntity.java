package com.reservation.tablereservationservice.infrastructure.reservation.entity;

import java.time.LocalDate;

import com.reservation.tablereservationservice.infrastructure.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
	name = "daily_slot_capacity",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_slot_date",
			columnNames = {"slot_id", "date"}
		)
	}
)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DailySlotCapacityEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long capacityId;

	@Column(nullable = false)
	private Long slotId;

	@Column(nullable = false)
	private LocalDate date;

	@Column(nullable = false)
	private Integer remainingCount;

	// @Version
	// @Column(nullable = false)
	// private Long version;

	@Builder
	public DailySlotCapacityEntity(Long slotId, LocalDate date, Integer remainingCount) {
		this.slotId = slotId;
		this.date = date;
		this.remainingCount = remainingCount;
	}

	public void updateRemainingCount(Integer remainingCount) {
		this.remainingCount = remainingCount;
	}
}
