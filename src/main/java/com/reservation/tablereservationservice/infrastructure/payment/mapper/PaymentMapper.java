package com.reservation.tablereservationservice.infrastructure.payment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.reservation.tablereservationservice.domain.payment.Payment;
import com.reservation.tablereservationservice.global.config.MapstructConfig;
import com.reservation.tablereservationservice.infrastructure.payment.entity.PaymentEntity;

@Mapper(config = MapstructConfig.class)
public interface PaymentMapper {

	PaymentMapper INSTANCE = Mappers.getMapper(PaymentMapper.class);

	Payment toDomain(PaymentEntity entity);

	PaymentEntity toEntity(Payment payment);
}
