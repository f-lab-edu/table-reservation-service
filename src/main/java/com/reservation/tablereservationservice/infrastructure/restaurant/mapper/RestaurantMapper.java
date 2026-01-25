package com.reservation.tablereservationservice.infrastructure.restaurant.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.global.config.MapstructConfig;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantEntity;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantSlotEntity;

@Mapper(config = MapstructConfig.class)
public interface RestaurantMapper {

	RestaurantMapper INSTANCE = Mappers.getMapper(RestaurantMapper.class);

	Restaurant toDomain(RestaurantEntity entity);

	RestaurantEntity toEntity(Restaurant restaurant);

	RestaurantSlot toDomain(RestaurantSlotEntity entity);

	RestaurantSlotEntity toEntity(RestaurantSlot restaurantSlot);

}
