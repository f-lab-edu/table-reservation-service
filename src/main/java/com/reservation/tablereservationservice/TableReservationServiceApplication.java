package com.reservation.tablereservationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TableReservationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TableReservationServiceApplication.class, args);
	}

}
