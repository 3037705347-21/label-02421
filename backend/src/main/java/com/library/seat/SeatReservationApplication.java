package com.library.seat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.library.seat.mapper")
@EnableScheduling
public class SeatReservationApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeatReservationApplication.class, args);
    }
}
