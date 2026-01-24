package com.reservation.tablereservationservice.presentation.reservation.controller;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.restaurant.CategoryCode;
import com.reservation.tablereservationservice.domain.restaurant.RegionCode;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.domain.user.UserRole;
import com.reservation.tablereservationservice.global.jwt.JwtProvider;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReservationControllerIntegrationTest {

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
	private LocalDate date;
	private LocalTime slotTime;

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
		this.date = LocalDate.of(2026, 1, 26);
		this.slotTime = LocalTime.of(19, 0);

		User owner = userRepository.save(User.builder()
			.email("owner@test.com")
			.name("사장님")
			.phone("010-0000-0001")
			.password("encrypted-password")
			.userRole(UserRole.OWNER)
			.build());

		User customer = userRepository.save(User.builder()
			.email("customer@test.com")
			.name("테스터")
			.phone("010-0000-0002")
			.password("encrypted-password")
			.userRole(UserRole.CUSTOMER)
			.build());

		// 토큰은 직접 발급
		this.ownerAccessToken = jwtProvider.createAccessToken(owner.getEmail(), UserRole.OWNER);
		this.customerAccessToken = jwtProvider.createAccessToken(customer.getEmail(), UserRole.CUSTOMER);

		Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
			.name("강남 한상")
			.regionCode(RegionCode.RG01)
			.categoryCode(CategoryCode.CT01)
			.address("서울 강남구 테헤란로 1")
			.ownerId(owner.getUserId())
			.build());

		RestaurantSlot slot = restaurantSlotRepository.save(RestaurantSlot.builder()
			.restaurantId(restaurant.getRestaurantId())
			.time(slotTime)
			.maxCapacity(10)
			.build());

		this.slotId = slot.getSlotId();

		dailySlotCapacityRepository.save(DailySlotCapacity.builder()
			.slotId(slotId)
			.date(date)
			.remainingCount(10)
			.maxCount(10)
			.version(0L)
			.build());
	}

	@Test
	@DisplayName("예약 요청 성공 - CUSTOMER 토큰이면 200 + 응답 바디 반환")
	void create_Success_WhenCustomerToken() {
		ReservationRequestDto request = new ReservationRequestDto(slotId, date, 2, "note");

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
			  .body("data.visitAt", equalTo("2026-01-26T19:00:00"));

	}

	@Test
	@DisplayName("예약 요청 실패 - OWNER 토큰이면 403")
	void create_Fail_WhenOwnerToken() {
		ReservationRequestDto request = new ReservationRequestDto(
			slotId,
			date,
			2,
			"note"
		);

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
	void create_Fail_WithoutToken() {
		ReservationRequestDto request = new ReservationRequestDto(
			slotId,
			date,
			2,
			"note"
		);

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
		ReservationRequestDto request = new ReservationRequestDto(
			null,
			null,
			0,
			"note"
		);

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
}
