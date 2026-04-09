package com.library.seat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.seat.dto.SeatDTO;
import com.library.seat.entity.Seat;

public interface SeatService {
    Page<Seat> listSeats(Integer page, Integer size, String area, Integer hasCharger, Integer nearWindow, Integer status);
    void addSeat(SeatDTO dto);
    void updateSeat(SeatDTO dto);
    void deleteSeat(Long id);
}
