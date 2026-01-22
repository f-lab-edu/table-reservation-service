package com.reservation.tablereservationservice.infrastructure.reservation.entity;

import java.time.LocalDate;

import com.reservation.tablereservationservice.infrastructure.common.entity.BaseTimeEntity;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantSlotEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "daily_slot_capacity",
	uniqueConstraints = {
		@UniqueConstraint(name = "uq_slot_date", columnNames = {"capacity_slot_id", "capacity_date"})
	})
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DailySlotCapacityEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long capacityId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "capacity_slot_id", nullable = false)
	private RestaurantSlotEntity slot;

	@Column(name = "capacity_date", nullable = false)
	private LocalDate date;

	@Column(name = "capacity_remaining_count", nullable = false)
	private Integer remainingCount;

	@Column(name = "capacity_max_count", nullable = false)
	private Integer maxCount;

	@Version
	@Column(name = "capacity_version", nullable = false)
	private Long version;

	@Builder
	public DailySlotCapacityEntity(RestaurantSlotEntity slot, LocalDate date, Integer remainingCount, Integer maxCount) {
		this.slot = slot;
		this.date = date;
		this.remainingCount = remainingCount;
		this.maxCount = maxCount;
	}

	public void updateRemainingCount(Integer remainingCount) {
		this.remainingCount = remainingCount;
	}
}
