package com.reservation.tablereservationservice.infrastructure.restaurant.entity;

import com.reservation.tablereservationservice.domain.restaurant.CategoryCode;
import com.reservation.tablereservationservice.domain.restaurant.RegionCode;
import com.reservation.tablereservationservice.infrastructure.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "restaurant")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RestaurantEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long restaurantId;

	@Column(nullable = false)
	private Long ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private RegionCode regionCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private CategoryCode categoryCode;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false)
	private String address;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(length = 100)
	private String mainMenuName;

	private Integer mainMenuPrice;

	@Builder
	public RestaurantEntity(Long ownerId, RegionCode regionCode, CategoryCode categoryCode, String name, String address,
		String description, String mainMenuName, Integer mainMenuPrice) {
		this.ownerId = ownerId;
		this.regionCode = regionCode;
		this.categoryCode = categoryCode;
		this.name = name;
		this.address = address;
		this.description = description;
		this.mainMenuName = mainMenuName;
		this.mainMenuPrice = mainMenuPrice;
	}
}
