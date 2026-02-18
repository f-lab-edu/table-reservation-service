package com.reservation.tablereservationservice.presentation.reservation.controller;

import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;
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
				.version(0L)
				.build()
		);
	}

	@Test
	@DisplayName("예약 요청 성공 - CUSTOMER 토큰이면 200 + 응답 바디 반환")
	void create_success_whenCustomerToken() {
		ReservationRequestDto request = new ReservationRequestDto(slotId, BASE_DATE, 2, "note");

		given()
			.contentType(ContentType.JSON)
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
	@DisplayName("내 예약 목록 조회 성공 - status=CONFIRMED면 확정건만 조회")
	void getReservations_me_success_onlyConfirmed() {
		// given: 예약 1건 생성
		Long reservationId = createReservation(customerAccessToken, slotId, BASE_DATE, 2, "note");

		given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + customerAccessToken)
			.queryParam("fromDate", BASE_DATE.minusDays(1).toString())
			.queryParam("toDate", BASE_DATE.plusDays(1).toString())
			.queryParam("status", ReservationStatus.CONFIRMED)
		.when()
			.get("/api/reservations/me")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("예약 조회 성공"))
			.body("data.content.size()", equalTo(1))
			.body("data.content[0].reservationId", equalTo(reservationId.intValue()))
			.body("data.content[0].partySize", equalTo(2))
			.body("data.content[0].status", equalTo("CONFIRMED"))
			.body("data.content[0].visitAt", startsWith(BASE_VISIT_AT.toString()));
	}

	@Test
	@DisplayName("내 예약 목록 조회 성공 - status 파라미터 없으면 CONFIRMED + CANCELED 모두 조회")
	void getReservations_me_success_whenStatusOmitted() {
		// given: 서로 다른 날짜로 2건 생성 후 1건 취소
		LocalDate confirmedDate = BASE_DATE;
		LocalDate cancelDate = BASE_DATE.plusDays(1);

		// cancelDate도 slot 생성
		dailySlotCapacityRepository.save(DailySlotCapacityFixture.capacity()
			.slotId(slotId)
			.date(cancelDate)
			.remainingCount(10)
			.version(0L)
			.build());

		createReservation(customerAccessToken, slotId, confirmedDate, 2, "confirmed");

		Long toCancelId = createReservation(customerAccessToken, slotId, cancelDate, 1, "cancel");
		cancelReservation(customerAccessToken, toCancelId);

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
			.body("data.content.status", containsInAnyOrder("CONFIRMED", "CANCELED"));
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
	@DisplayName("예약 취소 성공 - CUSTOMER 토큰이면 200 + DB status=CANCELED 반영")
	void cancel_success_whenCustomerToken() {
		// given
		Long reservationId = createReservation(customerAccessToken, slotId, BASE_DATE, 2, "note");

		// when & then (API 응답)
		given()
			.contentType(ContentType.JSON)
			.header("Authorization", "Bearer " + customerAccessToken)
		.when()
			.post("/api/reservations/{reservationId}/cancel", reservationId)
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("예약 취소 성공"))
			.body("data.reservationId", equalTo(reservationId.intValue()))
			.body("data.status", equalTo("CANCELED"));

		// then (DB 반영)
		Reservation stored = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new IllegalStateException("Reservation not found"));
		assertThat(stored.getStatus()).isEqualTo(ReservationStatus.CANCELED);
	}

	private Long createReservation(String accessToken, Long slotId, LocalDate date, int partySize, String note) {
		ReservationRequestDto request = new ReservationRequestDto(slotId, date, partySize, note);

		Integer reservationId =
			given()
				.contentType(ContentType.JSON)
				.header("Authorization", "Bearer " + accessToken)
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
