package com.reservation.tablereservationservice.infrastructure.reservation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.global.config.MapstructConfig;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.DailySlotCapacityEntity;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.ReservationEntity;

@Mapper(config = MapstructConfig.class)
public interface ReservationMapper {

	ReservationMapper INSTANCE = Mappers.getMapper(ReservationMapper.class);

	@Mapping(target = "slotId", source = "slot.slotId")
	DailySlotCapacity toDomain(DailySlotCapacityEntity entity);

	@Mapping(target = "slot", ignore = true)
	DailySlotCapacityEntity toEntity(DailySlotCapacity dailySlotCapacity);

	@Mapping(target = "slotId", source = "slot.slotId")
	Reservation toDomain(ReservationEntity entity);

	@Mapping(target = "slot", ignore = true)
	ReservationEntity toEntity(Reservation reservation);

}
