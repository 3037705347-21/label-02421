package com.library.seat.controller;

import com.library.seat.service.UserService;
import com.library.seat.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/list")
    public Result<?> list(@RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer size,
                          @RequestParam(required = false) String keyword) {
        return Result.success(userService.listUsers(page, size, keyword));
    }

    @PutMapping("/status/{id}")
    public Result<?> toggleStatus(@PathVariable Long id) {
        userService.toggleUserStatus(id);
        return Result.success();
    }
}
