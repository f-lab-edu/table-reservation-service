package com.reservation.tablereservationservice.global.config;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

@MapperConfig(
	componentModel = "spring",
	unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface MapstructConfig {
}