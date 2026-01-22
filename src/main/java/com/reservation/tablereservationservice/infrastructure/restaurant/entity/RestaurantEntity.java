package com.reservation.tablereservationservice.infrastructure.restaurant.entity;

import com.reservation.tablereservationservice.infrastructure.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

	@Column(name = "restaurant_owner_id", nullable = false)
	private Long ownerId;

	@Column(name = "restaurant_region_code_id", nullable = false, length = 10)
	private String regionCodeId;

	@Column(name = "restaurant_category_code_id", nullable = false, length = 10)
	private String categoryCodeId;

	@Column(name = "restaurant_name", nullable = false, length = 100)
	private String name;

	@Column(name = "restaurant_address", nullable = false)
	private String address;

	@Column(name = "restaurant_description", columnDefinition = "TEXT")
	private String description;

	@Column(name = "restaurant_main_menu_name", length = 100)
	private String mainMenuName;

	@Column(name = "restaurant_main_menu_price")
	private Integer mainMenuPrice;

	@Builder
	public RestaurantEntity(Long ownerId, String regionCodeId, String categoryCodeId, String name, String address,
		String description, String mainMenuName, Integer mainMenuPrice) {
		this.ownerId = ownerId;
		this.regionCodeId = regionCodeId;
		this.categoryCodeId = categoryCodeId;
		this.name = name;
		this.address = address;
		this.description = description;
		this.mainMenuName = mainMenuName;
		this.mainMenuPrice = mainMenuPrice;
	}
}
