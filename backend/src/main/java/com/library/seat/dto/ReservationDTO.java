package com.library.seat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ReservationDTO {
    @NotNull(message = "座位ID不能为空")
    private Long seatId;
    @NotNull(message = "预约日期不能为空")
    private LocalDate reserveDate;
    @NotNull(message = "时间段不能为空")
    private String timeSlot;
}
