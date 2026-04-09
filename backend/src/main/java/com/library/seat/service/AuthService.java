package com.library.seat.service;

import com.library.seat.dto.LoginDTO;
import com.library.seat.dto.RegisterDTO;
import com.library.seat.entity.User;

import java.util.Map;

public interface AuthService {
    Map<String, Object> login(LoginDTO dto);
    void register(RegisterDTO dto);
    User getCurrentUser(Long userId);
}
