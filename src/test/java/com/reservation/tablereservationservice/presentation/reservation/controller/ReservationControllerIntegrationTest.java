package com.reservation.tablereservationservice.presentation.reservation.controller;

import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.domain.user.UserRole;
import com.reservation.tablereservationservice.fixture.DailySlotCapacityFixture;
import com.reservation.tablereservationservice.fixture.RestaurantFixture;
import com.reservation.tablereservationservice.fixture.RestaurantSlotFixture;
import com.reservation.tablereservationservice.fixture.UserFixture;
import com.reservation.tablereservationservice.global.jwt.JwtProvider;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.CancelQueuePublisher;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReservationControllerIntegrationTest {

	private static final LocalDate BASE_DATE = LocalDate.of(2030, 1, 1);
	private static final LocalTime BASE_TIME = LocalTime.of(19, 0);
	private static final LocalDateTime BASE_VISIT_AT = LocalDateTime.of(2030, 1, 1, 19, 0);

	@LocalServerPort
	private int port;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private RestaurantRepository restaurantRepository;

	@Autowired
	private RestaurantSlotRepository restaurantSlotRepository;

	@Autowired
	private DailySlotCapacityRepository dailySlotCapacityRepository;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private ReservationStreamProperties streamProperties;

	@MockitoBean
	private CancelQueuePublisher cancelQueuePublisher;

	private Long slotId;
	private Long customerId;
	private String customerAccessToken;
	private String ownerAccessToken;

	@BeforeEach
	void setUp() {
		cleanupRedis();
		reservationRepository.deleteAll();
		dailySlotCapacityRepository.deleteAll();
		restaurantSlotRepository.deleteAll();
		restaurantRepository.deleteAll();
		userRepository.deleteAll();

		RestAssured.port = port;

		User owner = userRepository.save(UserFixture.owner().build());
		User customer = userRepository.save(UserFixture.customer().build());

		this.customerId = customer.getUserId();
		this.ownerAccessToken = jwtProvider.createAccessToken(owner.getEmail(), UserRole.OWNER);
		this.customerAccessToken = jwtProvider.createAccessToken(customer.getEmail(), UserRole.CUSTOMER);

		Restaurant restaurant = restaurantRepository.save(
			RestaurantFixture.restaurant()
				.ownerId(owner.getUserId())
				.build()
		);

		RestaurantSlot slot = restaurantSlotRepository.save(
			RestaurantSlotFixture.slot()
				.restaurantId(restaurant.getRestaurantId())
				.time(BASE_TIME)
				.maxCapacity(10)
				.build()
		);
		this.slotId = slot.getSlotId();

		dailySlotCapacityRepository.save(
			DailySlotCapacityFixture.capacity()
				.slotId(slotId)
				.date(BASE_DATE)
				.remainingCount(10)
								.build()
		);

		// Redis 좌석 선점용 remaining key 초기화
		redisTemplate.opsForValue().set(streamProperties.remainingKey(slotId, BASE_DATE.toString()), "10");
	}

	@AfterEach
	void tearDown() {
		cleanupRedis();
		reservationRepository.deleteAll();
		dailySlotCapacityRepository.deleteAll();
		restaurantSlotRepository.deleteAll();
		restaurantRepository.deleteAll();
		userRepository.deleteAll();
	}

	private void cleanupRedis() {
		Set<String> remaining = redisTemplate.keys(streamProperties.getRemainingKeyPrefix() + "*");
		if (remaining != null && !remaining.isEmpty()) redisTemplate.delete(remaining);
	}

	@Test
	@DisplayName("예약 요청 성공 - CUSTOMER 토큰이면 200 + 응답 바디 반환")
	void create_success_whenCustomerToken() {
		ReservationRequestDto request = new ReservationRequestDto(slotId, BASE_DATE, 2, "note");

		given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + customerAccessToken)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.body(request)
		.when()
			.post("/api/reservations")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("예약 접수 성공"))
			.body("data.reservationId", notNullValue())
			.body("data.partySize", equalTo(2))
			.body("data.status", equalTo("PENDING"))
			.body("data.depositAmount", notNullValue())
			.body("data.visitAt", startsWith(BASE_VISIT_AT.toString()));
	}

	@Test
	@DisplayName("예약 요청 실패 - OWNER 토큰이면 403")
	void create_fail_whenOwnerToken() {
		ReservationRequestDto request = new ReservationRequestDto(slotId, BASE_DATE, 2, "note");

		given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + ownerAccessToken)
			.body(request)
		.when()
			.post("/api/reservations")
		.then()
			.statusCode(403);
	}

	@Test
	@DisplayName("예약 요청 실패 - 토큰 없이 요청하면 401")
	void create_fail_withoutToken() {
		ReservationRequestDto request = new ReservationRequestDto(slotId, BASE_DATE, 2, "note");

		given()
			.contentType(ContentType.JSON)
			.body(request)
		.when()
			.post("/api/reservations")
		.then()
			.statusCode(401);
	}

	@Test
	@DisplayName("예약 요청 실패 - DTO 검증 실패면 400 + 상세 에러 반환")
	void create_fail_invalidRequestDto() {
		ReservationRequestDto request = new ReservationRequestDto(null, null, 0, "note");

		given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + customerAccessToken)
			.body(request)
		.when()
			.post("/api/reservations")
		.then()
			.statusCode(400)
			.body("code", equalTo(400))
			.body("data.slotId", notNullValue())
			.body("data.date", notNullValue())
			.body("data.partySize", notNullValue());
	}

	@Test
	@DisplayName("내 예약 목록 조회 성공 - status=PENDING이면 접수된 예약만 조회")
	void getReservations_me_success_onlyPending() {
		// given: 예약 1건 생성 (PENDING 상태)
		Long reservationId = createReservation(customerAccessToken, slotId, BASE_DATE, 2, "note");

		given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + customerAccessToken)
			.queryParam("fromDate", BASE_DATE.minusDays(1).toString())
			.queryParam("toDate", BASE_DATE.plusDays(1).toString())
			.queryParam("status", ReservationStatus.PENDING)
		.when()
			.get("/api/reservations/me")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("예약 조회 성공"))
			.body("data.content.size()", equalTo(1))
			.body("data.content[0].reservationId", equalTo(reservationId.intValue()))
			.body("data.content[0].partySize", equalTo(2))
			.body("data.content[0].status", equalTo("PENDING"))
			.body("data.content[0].visitAt", startsWith(BASE_VISIT_AT.toString()));
	}

	@Test
	@DisplayName("내 예약 목록 조회 성공 - status 파라미터 없으면 PENDING + CANCEL_PENDING 모두 조회")
	void getReservations_me_success_whenStatusOmitted() {
		// given: 서로 다른 날짜로 2건 생성 후 1건 취소
		LocalDate confirmedDate = BASE_DATE;
		LocalDate cancelDate = BASE_DATE.plusDays(1);

		// cancelDate도 capacity + Redis key 세팅
		dailySlotCapacityRepository.save(DailySlotCapacityFixture.capacity()
			.slotId(slotId)
			.date(cancelDate)
			.remainingCount(10)
						.build());
		redisTemplate.opsForValue().set(streamProperties.remainingKey(slotId, cancelDate.toString()), "10");

		createReservation(customerAccessToken, slotId, confirmedDate, 2, "confirmed");

		Reservation toCancel = reservationRepository.save(Reservation.builder()
			.userId(customerId)
			.slotId(slotId)
			.visitAt(LocalDateTime.of(cancelDate, BASE_TIME))
			.partySize(1)
			.note("cancel")
			.status(ReservationStatus.CONFIRMED)
			.idempotencyKey(UUID.randomUUID().toString())
			.build());
		cancelReservation(customerAccessToken, toCancel.getReservationId());

		given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + customerAccessToken)
			.queryParam("fromDate", BASE_DATE.minusDays(1).toString())
			.queryParam("toDate", BASE_DATE.plusDays(2).toString())
		.when()
			.get("/api/reservations/me")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("예약 조회 성공"))
			.body("data.content.size()", equalTo(2))
			.body("data.content.status", containsInAnyOrder("PENDING", "CANCEL_PENDING"));
	}

	@Test
	@DisplayName("점주 예약 목록 조회 성공 - owner의 가게 예약이 조회된다")
	void getReservations_owner_success() {
		// given: 고객 예약 1건 생성
		createReservation(customerAccessToken, slotId, BASE_DATE, 2, "note");

		given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + ownerAccessToken)
			.queryParam("fromDate", BASE_DATE.minusDays(1).toString())
			.queryParam("toDate", BASE_DATE.plusDays(1).toString())
			.queryParam("status", ReservationStatus.PENDING)
		.when()
			.get("/api/reservations/owner")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("예약 조회 성공"))
			.body("data.content.size()", equalTo(1))
			.body("data.content[0].partySize", equalTo(2))
			.body("data.content[0].visitAt", startsWith(BASE_VISIT_AT.toString()));
	}

	@Test
	@DisplayName("예약 취소 성공 - CUSTOMER 토큰이면 200 + DB status=CANCEL_PENDING 반영 (비동기)")
	void cancel_success_whenCustomerToken() {
		// given: 결제 완료 시나리오 — CONFIRMED 예약 직접 저장
		Reservation confirmed = reservationRepository.save(Reservation.builder()
			.userId(customerId)
			.slotId(slotId)
			.visitAt(BASE_VISIT_AT)
			.partySize(2)
			.note("note")
			.status(ReservationStatus.CONFIRMED)
			.idempotencyKey(UUID.randomUUID().toString())
			.build());
		Long reservationId = confirmed.getReservationId();

		// when & then (API 응답)
		given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + customerAccessToken)
		.when()
			.post("/api/reservations/{reservationId}/cancel", reservationId)
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("예약 취소 성공"));

		// then (DB 반영 — 비동기 취소이므로 CANCEL_PENDING)
		Reservation stored = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new IllegalStateException("Reservation not found"));
		assertThat(stored.getStatus()).isEqualTo(ReservationStatus.CANCEL_PENDING);
	}

	private Long createReservation(String accessToken, Long slotId, LocalDate date, int partySize, String note) {
		ReservationRequestDto request = new ReservationRequestDto(slotId, date, partySize, note);

		Integer reservationId =
			given()
				.contentType(ContentType.JSON)
				.header("Authorization", "Bearer " + accessToken)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.body(request)
			.when()
				.post("/api/reservations")
			.then()
				.statusCode(200)
				.extract()
				.path("data.reservationId");

		assertThat(reservationId).isNotNull();
		return reservationId.longValue();
	}

	private void cancelReservation(String accessToken, Long reservationId) {
		given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + accessToken)
		.when()
			.post("/api/reservations/{reservationId}/cancel", reservationId)
		.then()
			.statusCode(200);
	}
}
