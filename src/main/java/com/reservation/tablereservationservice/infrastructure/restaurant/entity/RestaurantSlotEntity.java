package com.reservation.tablereservationservice.infrastructure.restaurant.entity;

import java.time.LocalTime;

import com.reservation.tablereservationservice.infrastructure.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
	name = "restaurant_slot",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_restaurant_time",
			columnNames = {"restaurant_id", "time"}
		)
	}
)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RestaurantSlotEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long slotId;

	@Column(nullable = false)
	private Long restaurantId;

	@Column(nullable = false)
	private LocalTime time;

	@Column(nullable = false)
	private Integer maxCapacity;

	@Builder
	public RestaurantSlotEntity(Long restaurantId, LocalTime time, Integer maxCapacity) {
		this.restaurantId = restaurantId;
		this.time = time;
		this.maxCapacity = maxCapacity;
	}

}
