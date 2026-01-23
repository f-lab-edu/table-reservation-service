package com.reservation.tablereservationservice.domain.restaurant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RegionCode {
	RG01("RG01", "강남/역삼"),
    RG02("RG02", "신사/가로수길"),
    RG03("RG03", "압구정/청담"),
    RG04("RG04", "잠실/송파"),
    RG05("RG05", "성수"),
    RG06("RG06", "홍대/합정/연남"),
    RG07("RG07", "이태원/한남"),
    RG08("RG08", "여의도"),
    RG09("RG09", "광화문/종로"),
    RG10("RG10", "을지로/충무로"),
    RG11("RG11", "용산/삼각지");

	private final String code;
	private final String name;
}
