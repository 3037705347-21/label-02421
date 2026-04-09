package com.library.seat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.seat.dto.SeatDTO;
import com.library.seat.entity.Seat;
import com.library.seat.exception.BusinessException;
import com.library.seat.mapper.SeatMapper;
import com.library.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatMapper seatMapper;

    @Override
    public Page<Seat> listSeats(Integer page, Integer size, String area, Integer hasCharger, Integer nearWindow, Integer status) {
        LambdaQueryWrapper<Seat> wrapper = new LambdaQueryWrapper<>();
        if (area != null && !area.isEmpty()) {
            wrapper.eq(Seat::getArea, area);
        }
        if (hasCharger != null) {
            wrapper.eq(Seat::getHasCharger, hasCharger);
        }
        if (nearWindow != null) {
            wrapper.eq(Seat::getNearWindow, nearWindow);
        }
        if (status != null) {
            wrapper.eq(Seat::getStatus, status);
        }
        wrapper.orderByAsc(Seat::getArea, Seat::getSeatNo);
        return seatMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public void addSeat(SeatDTO dto) {
        Long count = seatMapper.selectCount(
                new LambdaQueryWrapper<Seat>().eq(Seat::getSeatNo, dto.getSeatNo()));
        if (count > 0) {
            throw new BusinessException("座位编号已存在");
        }
        Seat seat = new Seat();
        seat.setSeatNo(dto.getSeatNo());
        seat.setHasCharger(dto.getHasCharger() != null ? dto.getHasCharger() : 0);
        seat.setNearWindow(dto.getNearWindow() != null ? dto.getNearWindow() : 0);
        seat.setStatus(0);
        seat.setArea(dto.getArea());
        seat.setFloor(dto.getFloor() != null ? dto.getFloor() : 1);
        seatMapper.insert(seat);
        log.info("新增座位: {}", dto.getSeatNo());
    }

    @Override
    public void updateSeat(SeatDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("座位ID不能为空");
        }
        Seat seat = seatMapper.selectById(dto.getId());
        if (seat == null) {
            throw new BusinessException("座位不存在");
        }
        seat.setSeatNo(dto.getSeatNo());
        seat.setHasCharger(dto.getHasCharger());
        seat.setNearWindow(dto.getNearWindow());
        seat.setStatus(dto.getStatus());
        seat.setArea(dto.getArea());
        seat.setFloor(dto.getFloor());
        seatMapper.updateById(seat);
        log.info("更新座位: {}", dto.getSeatNo());
    }

    @Override
    public void deleteSeat(Long id) {
        Seat seat = seatMapper.selectById(id);
        if (seat == null) {
            throw new BusinessException("座位不存在");
        }
        seatMapper.deleteById(id);
        log.info("删除座位: {}", seat.getSeatNo());
    }
}
