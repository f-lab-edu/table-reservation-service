package com.reservation.tablereservationservice.infrastructure.notification.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.reservation.tablereservationservice.domain.notification.Notification;
import com.reservation.tablereservationservice.global.config.MapstructConfig;
import com.reservation.tablereservationservice.infrastructure.notification.entity.NotificationEntity;

@Mapper(config = MapstructConfig.class)
public interface NotificationMapper {

	NotificationMapper INSTANCE = Mappers.getMapper(NotificationMapper.class);

	Notification toDomain(NotificationEntity entity);

	NotificationEntity toEntity(Notification notification);
}
