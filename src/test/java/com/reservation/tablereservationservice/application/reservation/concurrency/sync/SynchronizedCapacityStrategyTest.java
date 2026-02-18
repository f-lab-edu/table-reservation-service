package com.reservation.tablereservationservice.application.reservation.concurrency.sync;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;

@SpringBootTest
@ActiveProfiles("test")
class SynchronizedCapacityStrategyTest {

	@Autowired
	private SynchronizedCapacityStrategy capacityStrategy;

	@Autowired
	private DailySlotCapacityRepository dailySlotCapacityRepository;

	@Autowired
	private RestaurantSlotRepository restaurantSlotRepository;

	private Long slotId;
	private LocalDate date;

	@BeforeEach
	void setUp() {
		// 테스트 날짜
		date = LocalDate.now();

		// 슬롯 생성
		RestaurantSlot slot = restaurantSlotRepository.save(
			RestaurantSlot.builder()
				.restaurantId(1L)
				.time(LocalTime.of(12, 0))
				.maxCapacity(10)
				.build()
		);
		slotId = slot.getSlotId();

		// 좌석 수량 생성 (10석)
		dailySlotCapacityRepository.save(
			DailySlotCapacity.builder()
				.slotId(slotId)
				.date(date)
				.remainingCount(10)
				.build()
		);
	}

	@Test
	@DisplayName("10명이 동시에 1명씩 예약하면 남은 좌석은 0이 된다. (synchronized)")
	void concurrency_test_1() throws InterruptedException {
		int threadCount = 10;

		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threadCount);

		Queue<Throwable> errors = new ConcurrentLinkedQueue<>();

		for (int i = 0; i < threadCount; i++) {
			pool.submit(() -> {
				try {
					ready.countDown();
					start.await();

					capacityStrategy.decrease(slotId, date, 1);
				} catch (Throwable t) {
					errors.add(t);
				} finally {
					done.countDown();
				}
			});
		}

		ready.await();
		start.countDown();
		done.await();
		pool.shutdown();

		assertThat(errors)
			.as("스레드 내부 예외 목록")
			.isEmpty();

		DailySlotCapacity result = dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date).orElseThrow();
		assertThat(result.getRemainingCount()).isEqualTo(0);

	}

	@Test
	@DisplayName("10명이 동시에 2명씩 예약하면 5명은 성공, 5명은 좌석부족이다. (synchronized)")
	void concurrency_test_2() throws InterruptedException {
		int threadCount = 10;

		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threadCount);

		Queue<Throwable> errors = new ConcurrentLinkedQueue<>();

		AtomicInteger success = new AtomicInteger();
		AtomicInteger notEnough = new AtomicInteger();

		for (int i = 0; i < threadCount; i++) {
			pool.submit(() -> {
				try {
					ready.countDown();
					start.await();

					capacityStrategy.decrease(slotId, date, 2);
					success.incrementAndGet();

				} catch (ReservationException e) {
					if (e.getErrorCode() == ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH) {
						notEnough.incrementAndGet();
					} else {
						errors.add(e);
					}
				} catch (Throwable t) {
					errors.add(t);
				} finally {
					done.countDown();
				}
			});
		}

		ready.await();
		start.countDown();
		done.await();
		pool.shutdown();

		assertThat(errors)
			.as("예상 못한 예외 목록")
			.isEmpty();

		assertThat(success.get()).isEqualTo(5);
		assertThat(notEnough.get()).isEqualTo(5);

		DailySlotCapacity result = dailySlotCapacityRepository.findBySlotIdAndDate(slotId, date).orElseThrow();
		assertThat(result.getRemainingCount()).isEqualTo(0);
	}
}
