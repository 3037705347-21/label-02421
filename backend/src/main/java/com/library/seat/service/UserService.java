package com.library.seat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.seat.entity.User;

public interface UserService {
    Page<User> listUsers(Integer page, Integer size, String keyword);
    void toggleUserStatus(Long id);
}
