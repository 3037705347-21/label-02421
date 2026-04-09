package com.library.seat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SeatDTO {
    private Long id;
    @NotNull(message = "座位编号不能为空")
    private String seatNo;
    private Integer hasCharger;
    private Integer nearWindow;
    private Integer status;
    private String area;
    private Integer floor;
}
