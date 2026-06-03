package com.reservation.tablereservationservice.application.reservation;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.reservation.tablereservationservice.application.reservation.facade.ReservationFacade;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.domain.restaurant.CategoryCode;
import com.reservation.tablereservationservice.domain.restaurant.RegionCode;
import com.reservation.tablereservationservice.domain.user.UserRole;
import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.DailySlotCapacityEntity;
import com.reservation.tablereservationservice.infrastructure.reservation.repository.DailySlotCapacityEntityRepository;
import com.reservation.tablereservationservice.infrastructure.reservation.repository.ReservationEntityRepository;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantEntity;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantSlotEntity;
import com.reservation.tablereservationservice.infrastructure.restaurant.repository.RestaurantEntityRepository;
import com.reservation.tablereservationservice.infrastructure.restaurant.repository.RestaurantSlotEntityRepository;
import com.reservation.tablereservationservice.infrastructure.user.entity.UserEntity;
import com.reservation.tablereservationservice.infrastructure.user.repository.UserEntityRepository;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

@SpringBootTest
@ActiveProfiles("test")
class SeatConcurrencyIntegrationTest {

	private static final int CAPACITY  = 10;
	private static final int CONCURRENT = 30;
	private static final LocalDate TEST_DATE = LocalDate.of(2030, 7, 1);

	@Autowired private ReservationFacade facade;
	@Autowired private StringRedisTemplate redisTemplate;
	@Autowired private ReservationStreamProperties streamProperties;

	@Autowired private UserEntityRepository userEntityRepository;
	@Autowired private RestaurantEntityRepository restaurantEntityRepository;
	@Autowired private RestaurantSlotEntityRepository restaurantSlotEntityRepository;
	@Autowired private DailySlotCapacityEntityRepository dailySlotCapacityEntityRepository;
	@Autowired private ReservationEntityRepository reservationEntityRepository;

	private Long slotId;
	private String seatKey;

	@BeforeEach
	void setUp() {
		// 사용자 30명 생성 (각 스레드가 독립적으로 다른 userId로 예약)
		IntStream.range(0, CONCURRENT).forEach(i ->
			userEntityRepository.save(UserEntity.builder()
				.email("seat-test-" + i + "@test.com")
				.password("$2a$10$dummyhashfortest")
				.name("SeatUser" + i)
				.phone("0100000" + String.format("%04d", i))
				.userRole(UserRole.CUSTOMER)
				.build())
		);

		// 매장 및 슬롯 생성
		RestaurantEntity restaurant = restaurantEntityRepository.save(
			RestaurantEntity.builder()
				.ownerId(9999L)
				.regionCode(RegionCode.RG01)
				.categoryCode(CategoryCode.CT01)
				.name("테스트 레스토랑")
				.address("서울 강남")
				.build()
		);

		RestaurantSlotEntity slot = restaurantSlotEntityRepository.save(
			RestaurantSlotEntity.builder()
				.restaurantId(restaurant.getRestaurantId())
				.time(LocalTime.of(19, 0))
				.maxCapacity(CAPACITY)
				.depositPerPerson(5000)
				.build()
		);
		slotId = slot.getSlotId();

		// 슬롯 용량 생성
		dailySlotCapacityEntityRepository.save(
			DailySlotCapacityEntity.builder()
				.slotId(slotId)
				.date(TEST_DATE)
				.remainingCount(CAPACITY)
				.build()
		);

		// Redis 잔여 좌석 초기화
		seatKey = streamProperties.remainingKey(slotId, TEST_DATE.toString());
		redisTemplate.opsForValue().set(seatKey, String.valueOf(CAPACITY));
	}

	@AfterEach
	void tearDown() {
		reservationEntityRepository.deleteAll();
		dailySlotCapacityEntityRepository.deleteAll();
		restaurantSlotEntityRepository.deleteAll();
		restaurantEntityRepository.deleteAll();
		userEntityRepository.findAll().stream()
			.filter(u -> u.getEmail().startsWith("seat-test-"))
			.forEach(u -> userEntityRepository.deleteById(u.getUserId()));

		redisTemplate.delete(seatKey);
		java.util.Set<String> keys = redisTemplate.keys("reservation:idempotency:*");
		if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
	}

	@Test
	@DisplayName("동시 30명 예약, 잔여 좌석 10 → 정확히 10명 성공, 20명 좌석 부족")
	void concurrent30_capacity10_exactly10Succeed() throws InterruptedException {
		AtomicInteger success          = new AtomicInteger();
		AtomicInteger capacityExhausted = new AtomicInteger();
		Queue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

		ExecutorService pool  = Executors.newFixedThreadPool(CONCURRENT);
		CountDownLatch ready  = new CountDownLatch(CONCURRENT);
		CountDownLatch start  = new CountDownLatch(1);
		CountDownLatch done   = new CountDownLatch(CONCURRENT);

		for (int i = 0; i < CONCURRENT; i++) {
			final String email = "seat-test-" + i + "@test.com";
			pool.submit(() -> {
				try {
					ready.countDown();
					start.await();

					facade.reserve(email,
						new ReservationRequestDto(slotId, TEST_DATE, 1, ""),
						UUID.randomUUID().toString()
					);
					success.incrementAndGet();

				} catch (ReservationException e) {
					if (e.getErrorCode() == ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH) {
						capacityExhausted.incrementAndGet();
					} else {
						unexpectedErrors.add(e);
					}
				} catch (Throwable t) {
					unexpectedErrors.add(t);
				} finally {
					done.countDown();
				}
			});
		}

		ready.await();
		start.countDown();
		done.await();
		pool.shutdown();

		assertThat(unexpectedErrors)
			.as("예상 외 예외 발생: " + unexpectedErrors)
			.isEmpty();

		// 성공 = 좌석 수
		assertThat(success.get()).isEqualTo(CAPACITY);
		assertThat(capacityExhausted.get()).isEqualTo(CONCURRENT - CAPACITY);

		// DB remaining_count = 0 (음수 없음)
		DailySlotCapacityEntity capacity = dailySlotCapacityEntityRepository
			.findBySlotIdAndDate(slotId, TEST_DATE).orElseThrow();
		assertThat(capacity.getRemainingCount()).isZero();

		// Redis 잔여 좌석 = 0 (음수 없음)
		assertThat(Integer.parseInt(redisTemplate.opsForValue().get(seatKey))).isZero();

		// DB 저장된 예약 수 = 성공 건수
		long savedCount = reservationEntityRepository.findAll().stream()
			.filter(r -> r.getStatus() == ReservationStatus.PENDING)
			.count();
		assertThat(savedCount).isEqualTo(CAPACITY);
	}
}
