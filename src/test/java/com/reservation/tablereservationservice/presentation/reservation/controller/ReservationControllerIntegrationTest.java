package com.reservation.tablereservationservice.presentation.reservation.controller;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
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
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReservationControllerIntegrationTest {

	private static final LocalDate TEST_DATE = LocalDate.of(2026, 1, 26);
	private static final LocalTime TEST_TIME = LocalTime.of(19, 0);
	private static final LocalDateTime TEST_VISIT_AT = LocalDateTime.of(TEST_DATE, TEST_TIME);

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

	private Long slotId;
	private String customerAccessToken;
	private String ownerAccessToken;

	@BeforeEach
	void setUp() {
		reservationRepository.deleteAll();
		dailySlotCapacityRepository.deleteAll();
		restaurantSlotRepository.deleteAll();
		restaurantRepository.deleteAll();
		userRepository.deleteAll();

		RestAssured.port = port;

		User owner = userRepository.save(UserFixture.owner().build());
		User customer = userRepository.save(UserFixture.customer().build());

		this.ownerAccessToken = jwtProvider.createAccessToken(owner.getEmail(), UserRole.OWNER);
		this.customerAccessToken = jwtProvider.createAccessToken(customer.getEmail(), UserRole.CUSTOMER);

		Restaurant restaurant = restaurantRepository.save(RestaurantFixture.restaurant()
			.ownerId(owner.getUserId())
			.build());

		RestaurantSlot slot = restaurantSlotRepository.save(RestaurantSlotFixture.slot()
			.restaurantId(restaurant.getRestaurantId())
			.time(TEST_TIME)
			.maxCapacity(10)
			.build());
		this.slotId = slot.getSlotId();

		dailySlotCapacityRepository.save(DailySlotCapacityFixture.capacity()
			.slotId(slotId)
			.date(TEST_DATE)
			.remainingCount(10)
			.version(0L)
			.build());
	}

	@Test
	@DisplayName("예약 요청 성공 - CUSTOMER 토큰이면 200 + 응답 바디 반환")
	void create_Success_WhenCustomerToken() {
		ReservationRequestDto request = new ReservationRequestDto(slotId, TEST_DATE, 2, "note");

		given().contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + customerAccessToken)
			.body(request)
		.when()
			.post("/api/reservations")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("예약 요청 성공"))
			.body("data.reservationId", notNullValue())
			.body("data.partySize", equalTo(2))
			.body("data.status", equalTo("CONFIRMED"))
			.body("data.visitAt", startsWith(TEST_VISIT_AT.toString()));
	}

	@Test
	@DisplayName("예약 요청 실패 - OWNER 토큰이면 403")
	void create_Fail_WhenOwnerToken() {
		ReservationRequestDto request = new ReservationRequestDto(slotId, TEST_DATE, 2, "note");

		given().contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + ownerAccessToken)
			.body(request)
		.when()
			.post("/api/reservations")
		.then()
			.statusCode(403);
	}

	@Test
	@DisplayName("예약 요청 실패 - 토큰 없이 요청하면 401")
	void create_Fail_WithoutToken() {
		ReservationRequestDto request = new ReservationRequestDto(slotId, TEST_DATE, 2, "note");

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
	void create_Fail_InvalidRequestDto() {
		// slotId/date도 null로 만들어서 "필수값 검증"을 확실하게 터뜨림
		ReservationRequestDto request = new ReservationRequestDto(null, null, 0, "note");

		given().contentType(ContentType.JSON)
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
	@DisplayName("내 예약 목록 조회 성공 - 확정건만 조회")
	void getReservations_me_success_whenCustomerRole() {
		User customer = userRepository.fetchByEmail("customer@test.com");

		reservationRepository.save(Reservation.builder()
			.userId(customer.getUserId())
			.slotId(slotId)
			.visitAt(TEST_VISIT_AT)
			.partySize(2)
			.note("note")
			.status(ReservationStatus.CONFIRMED)
			.build());

		given().contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + customerAccessToken)
			.queryParam("fromDate", "2026-01-01")
			.queryParam("toDate", "2026-02-01")
			.queryParam("status", ReservationStatus.CONFIRMED)
		.when()
			.get("/api/reservations/me")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("예약 조회 성공"))
			.body("data", notNullValue())
			.body("data.content.size()", equalTo(1))
			.body("data.content[0].partySize", equalTo(2))
			.body("data.content[0].status", equalTo("CONFIRMED"))
			.body("data.content[0].visitAt", startsWith(TEST_VISIT_AT.toString()));
	}

	@Test
	@DisplayName("내 예약 목록 조회 성공 - status 파라미터 없으면 CONFIRMED + CANCELED 모두 조회")
	void getReservations_me_success_whenStatusOmitted() {
		// given
		User customer = userRepository.fetchByEmail("customer@test.com");

		reservationRepository.save(Reservation.builder()
			.userId(customer.getUserId())
			.slotId(slotId)
			.visitAt(TEST_VISIT_AT)
			.partySize(2)
			.note("confirmed")
			.status(ReservationStatus.CONFIRMED)
			.build());

		reservationRepository.save(Reservation.builder()
			.userId(customer.getUserId())
			.slotId(slotId)
			.visitAt(TEST_VISIT_AT.plusHours(1))
			.partySize(2)
			.note("canceled")
			.status(ReservationStatus.CANCELED)
			.build());

		// when & then
		given().contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + customerAccessToken)
			.queryParam("fromDate", "2026-01-01")
			.queryParam("toDate", "2026-02-01")
		.when()
			.get("/api/reservations/me")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("예약 조회 성공"))
			.body("data.content.size()", equalTo(2))
			.body("data.content.status", containsInAnyOrder("CONFIRMED", "CANCELED"));
	}

	@Test
	@DisplayName("점주 예약 목록 조회 성공 - 확정건만 조회")
	void getReservations_owner_success_whenOwnerRole() {
		User customer = userRepository.fetchByEmail("customer@test.com");

		reservationRepository.save(Reservation.builder()
			.userId(customer.getUserId())
			.slotId(slotId)
			.visitAt(TEST_VISIT_AT)
			.partySize(2)
			.note("note")
			.status(ReservationStatus.CONFIRMED)
			.build());

		given().contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + ownerAccessToken)
			.queryParam("fromDate", "2026-01-01")
			.queryParam("toDate", "2026-02-01")
			.queryParam("status", ReservationStatus.CONFIRMED)
		.when()
			.get("/api/reservations/owner")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("예약 조회 성공"))
			.body("data", notNullValue())
			.body("data.content.size()", equalTo(1))
			.body("data.content[0].partySize", equalTo(2))
			.body("data.content[0].status", equalTo("CONFIRMED"))
			.body("data.content[0].visitAt", startsWith(TEST_VISIT_AT.toString()));
	}

	@Test
	@DisplayName("점주 예약 목록 조회 성공 - status 파라미터 없으면 CONFIRMED + CANCELED 모두 조회")
	void getReservations_owner_success_whenStatusOmitted() {
		// given
		User customer = userRepository.fetchByEmail("customer@test.com");

		reservationRepository.save(Reservation.builder()
			.userId(customer.getUserId())
			.slotId(slotId)
			.visitAt(TEST_VISIT_AT)
			.partySize(2)
			.note("confirmed")
			.status(ReservationStatus.CONFIRMED)
			.build());

		reservationRepository.save(Reservation.builder()
			.userId(customer.getUserId())
			.slotId(slotId)
			.visitAt(TEST_VISIT_AT.plusHours(1))
			.partySize(2)
			.note("canceled")
			.status(ReservationStatus.CANCELED)
			.build());

		// when & then
		given().contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + ownerAccessToken)
			.queryParam("fromDate", "2026-01-01")
			.queryParam("toDate", "2026-02-01")
		.when()
			.get("/api/reservations/owner")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("예약 조회 성공"))
			.body("data.content.size()", equalTo(2))
			.body("data.content.status", containsInAnyOrder("CONFIRMED", "CANCELED"));
	}
}
