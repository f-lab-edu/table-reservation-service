package com.reservation.tablereservationservice.application.reservation.concurrency.cas;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
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

import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;

@SpringBootTest
@ActiveProfiles("test")
class AtomicCasCapacityStrategyTest {

	@Autowired
	private AtomicCasCapacityStrategy capacityStrategy;

	private Long slotId;
	private LocalDate date;

	@BeforeEach
	void setUp() {
		capacityStrategy.clear();
		slotId = 1L;
		date = LocalDate.now();
		capacityStrategy.init(slotId, date, 10);
	}

	@Test
	@DisplayName("10명이 동시에 1명씩 예약하면 남은 좌석은 0이 된다. (CAS)")
	void concurrency_test_1() throws InterruptedException {
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

					capacityStrategy.decrease(slotId, date, 1);
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

		assertThat(errors).as("예상 못한 예외 목록").isEmpty();
		assertThat(notEnough.get()).isEqualTo(0);
		assertThat(success.get()).isEqualTo(10);
		assertThat(capacityStrategy.getRemaining(slotId, date)).isEqualTo(0);
	}

	@Test
	@DisplayName("10명이 동시에 2명씩 예약하면 5명은 성공, 5명은 좌석부족이다. (CAS)")
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

		assertThat(errors).as("예상 못한 예외 목록").isEmpty();
		assertThat(success.get()).isEqualTo(5);
		assertThat(notEnough.get()).isEqualTo(5);
		assertThat(capacityStrategy.getRemaining(slotId, date)).isEqualTo(0);
	}
}
