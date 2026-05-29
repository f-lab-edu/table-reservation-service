package com.reservation.tablereservationservice.presentation.restaurant.controller;

import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.restaurant.CategoryCode;
import com.reservation.tablereservationservice.domain.restaurant.RegionCode;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.fixture.DailySlotCapacityFixture;
import com.reservation.tablereservationservice.fixture.RestaurantFixture;
import com.reservation.tablereservationservice.fixture.RestaurantSlotFixture;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RestaurantControllerIntegrationTest {

	private static final LocalDate OPEN_DATE = LocalDate.of(2030, 1, 1);

	@LocalServerPort
	private int port;

	@Autowired
	private RestaurantRepository restaurantRepository;

	@Autowired
	private RestaurantSlotRepository restaurantSlotRepository;

	@Autowired
	private DailySlotCapacityRepository dailySlotCapacityRepository;

	@BeforeEach
	void setUp() {
		dailySlotCapacityRepository.deleteAll();
		restaurantSlotRepository.deleteAll();
		restaurantRepository.deleteAll();
		RestAssured.port = port;
	}

	@AfterEach
	void tearDown() {
		dailySlotCapacityRepository.deleteAll();
		restaurantSlotRepository.deleteAll();
		restaurantRepository.deleteAll();
	}

	@Test
	@DisplayName("매장 목록 조회 - 인증 없이 200 + 전체 목록 반환")
	void findRestaurants_noAuth_returns200() {
		saveRestaurant(RegionCode.RG01, CategoryCode.CT01);
		saveRestaurant(RegionCode.RG01, CategoryCode.CT07);

		given()
				.contentType(ContentType.JSON)
				.when()
				.get("/api/restaurants")
				.then()
				.statusCode(200)
				.body("code", equalTo(200))
				.body("message", equalTo("매장 목록 조회 성공"))
				.body("data.content.size()", equalTo(2))
				.body("data.hasNext", equalTo(false))
				.body("data.nextCursor", nullValue());
	}

	@Test
	@DisplayName("매장 목록 조회 - regionCode 필터로 해당 지역 매장만 반환")
	void findRestaurants_regionFilter_returnsMatchingOnly() {
		saveRestaurant(RegionCode.RG01, CategoryCode.CT01);
		saveRestaurant(RegionCode.RG01, CategoryCode.CT07);
		saveRestaurant(RegionCode.RG02, CategoryCode.CT01);

		given()
				.param("regionCode", "RG01")
		.when()
				.get("/api/restaurants")
		.then()
				.statusCode(200)
				.body("data.content.size()", equalTo(2))
				.body("data.content.regionCode", everyItem(equalTo("RG01")));
	}

	@Test
	@DisplayName("매장 목록 조회 - categoryCode 필터로 해당 카테고리 매장만 반환")
	void findRestaurants_categoryFilter_returnsMatchingOnly() {
		saveRestaurant(RegionCode.RG01, CategoryCode.CT01);
		saveRestaurant(RegionCode.RG02, CategoryCode.CT01);
		saveRestaurant(RegionCode.RG01, CategoryCode.CT07);

		given()
				.param("categoryCode", "CT01")
		.when()
				.get("/api/restaurants")
		.then()
				.statusCode(200)
				.body("data.content.size()", equalTo(2))
				.body("data.content.categoryCode", everyItem(equalTo("CT01")));
	}

	@Test
	@DisplayName("매장 목록 조회 - 커서 페이지네이션: hasNext=true, nextCursor 반환")
	void findRestaurants_cursor_hasNextTrue() {
		Restaurant r1 = saveRestaurant(RegionCode.RG01, CategoryCode.CT01);
		Restaurant r2 = saveRestaurant(RegionCode.RG01, CategoryCode.CT01);
		saveRestaurant(RegionCode.RG01, CategoryCode.CT01);

		Integer nextCursor = given()
				.param("size", 2)
		.when()
				.get("/api/restaurants")
		.then()
				.statusCode(200)
				.body("data.content.size()", equalTo(2))
				.body("data.hasNext", equalTo(true))
				.body("data.nextCursor", notNullValue())
				.extract()
				.path("data.nextCursor");

		// nextCursor는 두 번째 매장의 restaurantId
		assertThat(nextCursor.longValue()).isEqualTo(r2.getRestaurantId());
	}

	@Test
	@DisplayName("매장 목록 조회 - cursor로 다음 페이지 조회")
	void findRestaurants_withCursor_returnsNextPage() {
		Restaurant r1 = saveRestaurant(RegionCode.RG01, CategoryCode.CT01);
		Restaurant r2 = saveRestaurant(RegionCode.RG01, CategoryCode.CT01);
		Restaurant r3 = saveRestaurant(RegionCode.RG01, CategoryCode.CT01);

		// 첫 페이지: r1, r2 (size=2)
		// cursor=r2.id로 다음 페이지 요청
		given()
				.param("size", 2)
				.param("cursor", r2.getRestaurantId())
		.when()
				.get("/api/restaurants")
		.then()
				.statusCode(200)
				.body("data.content.size()", equalTo(1))
				.body("data.content[0].restaurantId", equalTo(r3.getRestaurantId().intValue()))
				.body("data.hasNext", equalTo(false))
				.body("data.nextCursor", nullValue());
	}

	@Test
	@DisplayName("슬롯 조회 - 오픈된 슬롯만 반환 (미오픈 슬롯 제외)")
	void findAvailableSlots_returnsOnlyOpenedSlots() {
		Restaurant restaurant = saveRestaurant(RegionCode.RG01, CategoryCode.CT01);
		RestaurantSlot openSlot = saveSlot(restaurant.getRestaurantId(), LocalTime.of(18, 0));
		RestaurantSlot closedSlot = saveSlot(restaurant.getRestaurantId(), LocalTime.of(20, 0));

		// openSlot만 DailySlotCapacity 등록
		dailySlotCapacityRepository.save(
				DailySlotCapacityFixture.capacity()
						.slotId(openSlot.getSlotId())
						.date(OPEN_DATE)
						.remainingCount(4)
						.build()
		);

		given()
				.param("date", OPEN_DATE.toString())
		.when()
				.get("/api/restaurants/{restaurantId}/slots", restaurant.getRestaurantId())
		.then()
				.statusCode(200)
				.body("code", equalTo(200))
				.body("message", equalTo("슬롯 조회 성공"))
				.body("data.size()", equalTo(1))
				.body("data[0].slotId", equalTo(openSlot.getSlotId().intValue()))
				.body("data[0].remainingCount", equalTo(4));
	}

	@Test
	@DisplayName("슬롯 조회 - 해당 날짜 미오픈 시 빈 목록 반환")
	void findAvailableSlots_noCapacity_returnsEmpty() {
		Restaurant restaurant = saveRestaurant(RegionCode.RG01, CategoryCode.CT01);
		saveSlot(restaurant.getRestaurantId(), LocalTime.of(18, 0));

		given()
				.param("date", OPEN_DATE.toString())
		.when()
				.get("/api/restaurants/{restaurantId}/slots", restaurant.getRestaurantId())
		.then()
				.statusCode(200)
				.body("data.size()", equalTo(0));
	}

	@Test
	@DisplayName("슬롯 조회 - date 파라미터 없으면 400")
	void findAvailableSlots_missingDate_returns400() {
		given()
				.when()
				.get("/api/restaurants/1/slots")
				.then()
				.statusCode(400);
	}

	private Restaurant saveRestaurant(RegionCode regionCode, CategoryCode categoryCode) {
		return restaurantRepository.save(
				RestaurantFixture.restaurant()
						.regionCode(regionCode)
						.categoryCode(categoryCode)
						.build()
		);
	}

	private RestaurantSlot saveSlot(Long restaurantId, LocalTime time) {
		return restaurantSlotRepository.save(
				RestaurantSlotFixture.slot()
						.restaurantId(restaurantId)
						.time(time)
						.build()
		);
	}
}
