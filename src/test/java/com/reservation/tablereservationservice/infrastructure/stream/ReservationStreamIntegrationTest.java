package com.reservation.tablereservationservice.infrastructure.stream;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.fixture.DailySlotCapacityFixture;
import com.reservation.tablereservationservice.fixture.RestaurantFixture;
import com.reservation.tablereservationservice.fixture.RestaurantSlotFixture;
import com.reservation.tablereservationservice.fixture.UserFixture;
import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ReservationStreamIntegrationTest {

	private static final Long PUBLISHER_TEST_SLOT_ID = 999L;
	private static final LocalDate DATE = LocalDate.of(2030, 6, 1);
	private static final LocalTime TIME = LocalTime.of(19, 0);

	@Container
	@SuppressWarnings("resource")
	static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
		.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
	}

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private ReservationStreamPublisher publisher;

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RestaurantRepository restaurantRepository;

	@Autowired
	private RestaurantSlotRepository restaurantSlotRepository;

	@Autowired
	private DailySlotCapacityRepository dailySlotCapacityRepository;

	@Autowired
	private ReservationStreamProperties streamProperties;

	@AfterEach
	void tearDown() {
		reservationRepository.deleteAll();
		dailySlotCapacityRepository.deleteAll();
		restaurantSlotRepository.deleteAll();
		restaurantRepository.deleteAll();
		userRepository.deleteAll();

		// 테스트 중 설정한 Redis remaining key 전체 삭제
		Set<String> remainingKeys = redisTemplate.keys(streamProperties.getRemainingKeyPrefix() + "*");
		if (remainingKeys != null && !remainingKeys.isEmpty()) {
			redisTemplate.delete(remainingKeys);
		}
	}

	@Test
	@DisplayName("publish - Redis remaining key 없음(슬롯 미오픈) → SLOT_NOT_OPENED 예외")
	void publish_슬롯미오픈_예외() {
		// given: remaining key를 Redis에 설정하지 않음 (슬롯 미오픈 상태)
		ReservationRequestMessage message = new ReservationRequestMessage(
			"stream-test@test.com", PUBLISHER_TEST_SLOT_ID, DATE, 2, "note"
		);

		// when & then
		assertThatThrownBy(() -> publisher.publish(message))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_SLOT_NOT_OPENED));
	}

	@Test
	@DisplayName("publish - 좌석 없음 → CAPACITY_NOT_ENOUGH 예외")
	void publish_좌석없음_예외() {
		// given: remaining key = "0" (좌석 소진)
		redisTemplate.opsForValue().set(
			streamProperties.remainingKey(PUBLISHER_TEST_SLOT_ID, DATE.toString()), "0"
		);

		ReservationRequestMessage message = new ReservationRequestMessage(
			"stream-test@test.com", PUBLISHER_TEST_SLOT_ID, DATE, 2, "note"
		);

		// when & then
		assertThatThrownBy(() -> publisher.publish(message))
			.isInstanceOf(ReservationException.class)
			.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
				.isEqualTo(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH));
	}

	@Test
	@DisplayName("publish 성공 - Lua 스크립트가 DECR + XADD를 원자적으로 실행한다")
	void publish_성공_원자적_DECR_XADD() {
		// given: remaining = 5
		redisTemplate.opsForValue().set(
			streamProperties.remainingKey(PUBLISHER_TEST_SLOT_ID, DATE.toString()), "5"
		);

		ReservationRequestMessage message = new ReservationRequestMessage(
			"stream-test@test.com", PUBLISHER_TEST_SLOT_ID, DATE, 2, "note"
		);

		// when
		publisher.publish(message);

		// then 1: remaining이 5 → 4로 차감됨 (DECR 확인)
		String remaining = redisTemplate.opsForValue().get(
			streamProperties.remainingKey(PUBLISHER_TEST_SLOT_ID, DATE.toString())
		);
		assertThat(remaining).isEqualTo("4");

		// then 2: 해당 파티션 스트림에 메시지가 적재됨 (XADD 확인)
		String streamKey = streamProperties.resolveStreamKey(PUBLISHER_TEST_SLOT_ID);
		Long streamLen = redisTemplate.opsForStream().size(streamKey);
		assertThat(streamLen).isGreaterThanOrEqualTo(1L);
	}

	@Test
	@DisplayName("submitReservation → Redis 스트림 적재 → Consumer 비동기 처리 → DB 예약 CONFIRMED 생성")
	void submitReservation_Consumer처리_예약생성() {
		// given: DB 엔티티 세팅
		User customer = userRepository.save(UserFixture.customer().build());
		User owner = userRepository.save(UserFixture.owner().build());

		Restaurant restaurant = restaurantRepository.save(
			RestaurantFixture.restaurant().ownerId(owner.getUserId()).build()
		);

		RestaurantSlot slot = restaurantSlotRepository.save(
			RestaurantSlotFixture.slot()
				.restaurantId(restaurant.getRestaurantId())
				.time(TIME)
				.maxCapacity(10)
				.build()
		);

		dailySlotCapacityRepository.save(
			DailySlotCapacityFixture.capacity()
				.slotId(slot.getSlotId())
				.date(DATE)
				.remainingCount(5)
				.build()
		);

		redisTemplate.opsForValue().set(
			streamProperties.remainingKey(slot.getSlotId(), DATE.toString()), "5"
		);

		// when: submitReservation → Lua 스크립트(DECR + XADD) → 스트림 적재
		reservationService.submitReservation(
			customer.getEmail(),
			new ReservationRequestDto(slot.getSlotId(), DATE, 2, "note")
		);

		// then: Consumer가 100ms 폴링 주기로 메시지를 수신 → handleReservationRequest → DB INSERT
		await()
			.atMost(Duration.ofSeconds(10))
			.pollInterval(Duration.ofMillis(200))
			.untilAsserted(() ->
				assertThat(reservationRepository.existsByUserIdAndVisitAtAndStatus(
					customer.getUserId(),
					DATE.atTime(TIME),
					ReservationStatus.CONFIRMED
				)).isTrue()
			);
	}
}
