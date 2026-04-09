package com.library.seat.controller;

import com.library.seat.aop.LogOperation;
import com.library.seat.dto.ReservationDTO;
import com.library.seat.service.ReservationService;
import com.library.seat.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @LogOperation(module = "预约管理", action = "创建", detail = "创建座位预约")
    public Result<?> create(HttpServletRequest request, @Valid @RequestBody ReservationDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        reservationService.createReservation(userId, dto);
        return Result.success();
    }

    @GetMapping("/my")
    public Result<?> myReservations(HttpServletRequest request,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(reservationService.myReservations(userId, page, size));
    }

    @PutMapping("/cancel/{id}")
    @LogOperation(module = "预约管理", action = "取消", detail = "取消预约")
    public Result<?> cancel(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        reservationService.cancelReservation(userId, id);
        return Result.success();
    }

    @PutMapping("/checkin/{id}")
    @LogOperation(module = "预约管理", action = "签到", detail = "预约签到")
    public Result<?> checkIn(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        reservationService.checkIn(userId, id);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<?> list(@RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer size,
                          @RequestParam(required = false) Integer status) {
        return Result.success(reservationService.allReservations(page, size, status));
    }
}
