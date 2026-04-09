package com.library.seat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.seat.dto.ReservationDTO;
import com.library.seat.entity.Reservation;

import java.util.Map;

public interface ReservationService {
    void createReservation(Long userId, ReservationDTO dto);
    Page<Map<String, Object>> myReservations(Long userId, Integer page, Integer size);
    void cancelReservation(Long userId, Long id);
    void checkIn(Long userId, Long id);
    Page<Map<String, Object>> allReservations(Integer page, Integer size, Integer status);
}
