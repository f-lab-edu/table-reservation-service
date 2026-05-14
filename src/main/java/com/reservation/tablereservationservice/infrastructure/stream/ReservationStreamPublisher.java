package com.reservation.tablereservationservice.infrastructure.stream;

import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationStreamPublisher {

	// 파티션 스트림 1개당 최대 메시지 수
	// 이 이상 쌓이면 오래된 메시지부터 자동 삭제 (MAXLEN ~ 방식)
	private static final String STREAM_MAX_LEN = "10000";

	private final StringRedisTemplate redisTemplate;
	private final ReservationStreamProperties streamProperties;

	private RedisScript<Long> submitScript;

	@PostConstruct
	void loadScript() {
		// ClassPathResource로 Lua 파일을 로드하면 SHA1 캐시가 적용된다.
		// 동일 스크립트를 반복 실행할 때 Redis 서버에 스크립트 내용을 매번 전송하지 않고 SHA1 해시만 보내므로 네트워크 비용이 줄어든다.
		submitScript = RedisScript.of(new ClassPathResource("scripts/reservation_submit.lua"), Long.class);
	}

	/**
	 * Lua 스크립트로 DECR + XADD를 원자적으로 실행한다.
	 * Redis 단일 스레드 + Lua 원자성 덕분에 "잔여 좌석 확인 → 차감 → 스트림 적재" 세가지 동작이 분리되지 않는다.
	 *
	 * @throws ReservationException 좌석 없음 또는 슬롯 미오픈
	 */
	public void publish(ReservationRequestMessage message) {
		String remainingKey = streamProperties.remainingKey(message.slotId(), message.date().toString());
		String streamKey = streamProperties.resolveStreamKey(message.slotId());

		Long result = redisTemplate.execute(
			submitScript,
			List.of(remainingKey, streamKey),
			message.userEmail(),
			String.valueOf(message.slotId()),
			message.date().toString(),
			String.valueOf(message.partySize()),
			message.note() != null ? message.note() : "",
			STREAM_MAX_LEN   // ARGV[6]: Lua 스크립트의 MAXLEN 값
		);

		if (result == -2L) {
			log.warn("[STREAM_PUBLISH] 슬롯 미오픈 slotId={}, date={}", message.slotId(), message.date());
			throw new ReservationException(ErrorCode.RESERVATION_SLOT_NOT_OPENED);
		}

		if (result == -1L) {
			log.info("[STREAM_PUBLISH] 좌석 없음 slotId={}, date={}", message.slotId(), message.date());
			throw new ReservationException(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH);
		}

		log.info(
			"[STREAM_PUBLISH] 성공 email={}, slotId={}, stream={}",
			message.userEmail(),
			message.slotId(),
			streamKey
		);
	}
}
