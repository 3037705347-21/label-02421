package com.library.seat.controller;

import com.library.seat.aop.LogOperation;
import com.library.seat.dto.SeatDTO;
import com.library.seat.service.SeatService;
import com.library.seat.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seat")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/list")
    public Result<?> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Integer hasCharger,
            @RequestParam(required = false) Integer nearWindow,
            @RequestParam(required = false) Integer status) {
        return Result.success(seatService.listSeats(page, size, area, hasCharger, nearWindow, status));
    }

    @PostMapping
    @LogOperation(module = "座位管理", action = "新增", detail = "新增座位")
    public Result<?> add(@Valid @RequestBody SeatDTO dto) {
        seatService.addSeat(dto);
        return Result.success();
    }

    @PutMapping
    @LogOperation(module = "座位管理", action = "修改", detail = "修改座位")
    public Result<?> update(@Valid @RequestBody SeatDTO dto) {
        seatService.updateSeat(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @LogOperation(module = "座位管理", action = "删除", detail = "删除座位")
    public Result<?> delete(@PathVariable Long id) {
        seatService.deleteSeat(id);
        return Result.success();
    }
}
