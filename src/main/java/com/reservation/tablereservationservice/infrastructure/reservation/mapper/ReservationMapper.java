package com.reservation.tablereservationservice.infrastructure.reservation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.global.config.MapstructConfig;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.DailySlotCapacityEntity;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.ReservationEntity;

@Mapper(config = MapstructConfig.class)
public interface ReservationMapper {

	ReservationMapper INSTANCE = Mappers.getMapper(ReservationMapper.class);

	DailySlotCapacity toDomain(DailySlotCapacityEntity entity);

	DailySlotCapacityEntity toEntity(DailySlotCapacity dailySlotCapacity);

	Reservation toDomain(ReservationEntity entity);

	ReservationEntity toEntity(Reservation reservation);

}
