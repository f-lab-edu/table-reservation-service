package com.reservation.tablereservationservice.infrastructure.restaurant.entity;

import java.time.LocalTime;

import com.reservation.tablereservationservice.infrastructure.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "restaurant_slot")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RestaurantSlotEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "restaurant_slot_id")
	private Long slotId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "restaurant_slot_restaurant_id", nullable = false)
	private RestaurantEntity restaurant;

	@Column(name = "restaurant_slot_time", nullable = false)
	private LocalTime time;

	@Column(name = "restaurant_slot_max_capacity", nullable = false)
	private Integer maxCapacity;

	@Builder
	public RestaurantSlotEntity(RestaurantEntity restaurant, LocalTime time, Integer maxCapacity) {
		this.restaurant = restaurant;
		this.time = time;
		this.maxCapacity = maxCapacity;
	}
}
