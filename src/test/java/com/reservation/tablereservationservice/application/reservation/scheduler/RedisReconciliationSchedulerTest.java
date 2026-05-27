package com.reservation.tablereservationservice.application.reservation.scheduler;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.fixture.DailySlotCapacityFixture;
import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;

@ExtendWith(MockitoExtension.class)
class RedisReconciliationSchedulerTest {

	@Mock
	private DailySlotCapacityRepository dailySlotCapacityRepository;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ReservationStreamProperties streamProperties;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@InjectMocks
	private RedisReconciliationScheduler scheduler;

	@Test
	@DisplayName("재동기화 - 조회된 모든 capacity를 Redis에 갱신한다")
	void syncCapacityToRedis_success_setsAllKeys() {
		// given
		DailySlotCapacity capacity1 = DailySlotCapacityFixture.capacity()
				.slotId(1L).date(LocalDate.now().plusDays(1)).remainingCount(4).build();
		DailySlotCapacity capacity2 = DailySlotCapacityFixture.capacity()
				.slotId(1L).date(LocalDate.now().plusDays(2)).remainingCount(3).build();
		DailySlotCapacity capacity3 = DailySlotCapacityFixture.capacity()
				.slotId(2L).date(LocalDate.now().plusDays(1)).remainingCount(2).build();

		given(dailySlotCapacityRepository.findAllFromDate(any(LocalDate.class)))
				.willReturn(List.of(capacity1, capacity2, capacity3));
		given(streamProperties.remainingKey(anyLong(), anyString())).willAnswer(inv ->
				"reservation:remaining:" + inv.getArgument(0) + ":" + inv.getArgument(1));
		given(redisTemplate.opsForValue()).willReturn(valueOperations);

		// when
		scheduler.syncCapacityToRedis();

		// then
		verify(valueOperations, times(3)).set(anyString(), anyString(), eq(7L), eq(TimeUnit.DAYS));
	}

	@Test
	@DisplayName("재동기화 - Redis 키에 올바른 remaining_count가 저장된다")
	void syncCapacityToRedis_success_setsCorrectValues() {
		// given
		DailySlotCapacity capacity = DailySlotCapacityFixture.capacity()
				.slotId(1L).date(LocalDate.now().plusDays(1)).remainingCount(3).build();

		given(dailySlotCapacityRepository.findAllFromDate(any())).willReturn(List.of(capacity));
		given(streamProperties.remainingKey(1L, capacity.getDate().toString()))
				.willReturn("reservation:remaining:1:" + capacity.getDate());
		given(redisTemplate.opsForValue()).willReturn(valueOperations);

		// when
		scheduler.syncCapacityToRedis();

		// then
		verify(valueOperations).set(
				eq("reservation:remaining:1:" + capacity.getDate()),
				eq("3"),
				eq(7L),
				eq(TimeUnit.DAYS)
		);
	}

	@Test
	@DisplayName("재동기화 - 조회 결과가 없으면 Redis 갱신을 하지 않는다")
	void syncCapacityToRedis_emptyCapacities_doesNothing() {
		// given
		given(dailySlotCapacityRepository.findAllFromDate(any())).willReturn(List.of());

		// when
		scheduler.syncCapacityToRedis();

		// then
		verifyNoInteractions(redisTemplate);
	}
}
