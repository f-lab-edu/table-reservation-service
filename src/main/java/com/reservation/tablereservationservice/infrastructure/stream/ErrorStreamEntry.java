package com.reservation.tablereservationservice.infrastructure.stream;

import java.util.HashMap;
import java.util.Map;

/**
 * Error Stream(DLQ)에 저장되는 메시지 구조
 * 원본 예약 요청 필드 + 장애 메타데이터로 구성된다.
 */
public record ErrorStreamEntry(
	String userEmail,
	String slotId,
	String date,
	String partySize,
	String note,
	String originalStream,
	String originalId,
	String deliveryCount,
	String failedAt
) {

	public static ErrorStreamEntry of(
		Map<String, String> originalBody, String originalStream, String originalId,
		long deliveryCount
	) {
		return new ErrorStreamEntry(
			originalBody.get("userEmail"),
			originalBody.get("slotId"),
			originalBody.get("date"),
			originalBody.get("partySize"),
			originalBody.get("note"),
			originalStream,
			originalId,
			String.valueOf(deliveryCount),
			String.valueOf(System.currentTimeMillis())
		);
	}

	public static ErrorStreamEntry fromMap(Map<String, String> body) {
		return new ErrorStreamEntry(
			body.get("userEmail"),
			body.get("slotId"),
			body.get("date"),
			body.get("partySize"),
			body.get("note"),
			body.get("originalStream"),
			body.get("originalId"),
			body.get("deliveryCount"),
			body.get("failedAt")
		);
	}

	/**
	 * Redis Stream XADD 용 Map 직렬화
	 */
	public Map<String, String> toMap() {
		Map<String, String> map = new HashMap<>();
		map.put("userEmail", userEmail);
		map.put("slotId", slotId);
		map.put("date", date);
		map.put("partySize", partySize);
		map.put("note", note != null ? note : "");
		map.put("originalStream", originalStream);
		map.put("originalId", originalId);
		map.put("deliveryCount", deliveryCount);
		map.put("failedAt", failedAt);
		return map;
	}
}
