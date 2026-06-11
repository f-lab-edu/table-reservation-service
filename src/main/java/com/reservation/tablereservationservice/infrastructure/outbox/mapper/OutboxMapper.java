package com.reservation.tablereservationservice.infrastructure.outbox.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.reservation.tablereservationservice.domain.outbox.OutboxEvent;
import com.reservation.tablereservationservice.global.config.MapstructConfig;
import com.reservation.tablereservationservice.infrastructure.outbox.entity.OutboxEventEntity;

@Mapper(config = MapstructConfig.class)
public interface OutboxMapper {

	OutboxMapper INSTANCE = Mappers.getMapper(OutboxMapper.class);

	OutboxEvent toDomain(OutboxEventEntity entity);

	OutboxEventEntity toEntity(OutboxEvent event);
}
