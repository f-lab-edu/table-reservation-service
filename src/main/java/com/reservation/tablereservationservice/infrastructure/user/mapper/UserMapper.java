package com.reservation.tablereservationservice.infrastructure.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.global.config.MapstructConfig;
import com.reservation.tablereservationservice.infrastructure.user.entity.UserEntity;

@Mapper(config = MapstructConfig.class)
public interface UserMapper {

	UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

	User toDomain(UserEntity entity);

	UserEntity toEntity(User user);
}