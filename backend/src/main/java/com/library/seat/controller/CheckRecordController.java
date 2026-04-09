package com.library.seat.controller;

import com.library.seat.aop.LogOperation;
import com.library.seat.service.CheckRecordService;
import com.library.seat.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/check")
@RequiredArgsConstructor
public class CheckRecordController {

    private final CheckRecordService checkRecordService;

    @PostMapping("/upload")
    @LogOperation(module = "打卡管理", action = "上传", detail = "上传清洁打卡照片")
    public Result<?> upload(HttpServletRequest request,
                            @RequestParam Long reservationId,
                            @RequestParam MultipartFile file,
                            @RequestParam(required = false) String remark) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> result = checkRecordService.uploadCheck(userId, reservationId, file, remark);
        return Result.success(result);
    }

    @GetMapping("/{reservationId}")
    public Result<?> getByReservation(@PathVariable Long reservationId) {
        return Result.success(checkRecordService.getByReservationId(reservationId));
    }

    @GetMapping("/list")
    public Result<?> list(@RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(checkRecordService.listCheckRecords(page, size));
    }

    @PutMapping("/review/{id}")
    @LogOperation(module = "打卡管理", action = "审核", detail = "审核打卡记录")
    public Result<?> review(@PathVariable Long id, @RequestParam Integer cleanPassed) {
        checkRecordService.reviewCheck(id, cleanPassed);
        return Result.success();
    }
}
