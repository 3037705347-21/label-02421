package com.library.seat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.seat.entity.OperationLog;
import com.library.seat.mapper.OperationLogMapper;
import com.library.seat.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogMapper operationLogMapper;

    @GetMapping("/list")
    public Result<?> list(@RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer size) {
        Page<OperationLog> logPage = operationLogMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<OperationLog>().orderByDesc(OperationLog::getCreateTime));
        return Result.success(logPage);
    }
}
