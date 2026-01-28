package com.reservation.tablereservationservice.domain.restaurant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CategoryCode {

	CT01("CT01", "한식"),
	CT02("CT02", "일식"),
	CT03("CT03", "중식"),
	CT04("CT04", "양식"),
	CT05("CT05", "분식"),
	CT06("CT06", "카페/디저트"),
	CT07("CT07", "고기/구이"),
	CT08("CT08", "술집/바");

	private final String code;
	private final String name;

}
