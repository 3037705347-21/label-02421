package com.library.seat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.seat.dto.ReservationDTO;
import com.library.seat.entity.Reservation;
import com.library.seat.entity.Seat;
import com.library.seat.entity.User;
import com.library.seat.exception.BusinessException;
import com.library.seat.mapper.ReservationMapper;
import com.library.seat.mapper.SeatMapper;
import com.library.seat.mapper.UserMapper;
import com.library.seat.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public void createReservation(Long userId, ReservationDTO dto) {
        // 校验日期不能是过去
        if (dto.getReserveDate().isBefore(LocalDate.now())) {
            throw new BusinessException("预约日期不能早于今天");
        }
        // 校验当天已完全过去的时间段不可预约（当前时间在时段内的仍可预约）
        if (dto.getReserveDate().isEqual(LocalDate.now())) {
            String timeSlot = dto.getTimeSlot();
            if (timeSlot != null && timeSlot.contains("-")) {
                String endTimeStr = timeSlot.split("-")[1].trim();
                LocalTime slotEnd = LocalTime.parse(endTimeStr);
                if (LocalTime.now().isAfter(slotEnd)) {
                    throw new BusinessException("该时间段已结束，请选择其他时间段");
                }
            }
        }
        // 校验座位是否存在
        Seat seat = seatMapper.selectById(dto.getSeatId());
        if (seat == null) {
            throw new BusinessException("座位不存在");
        }
        if (seat.getStatus() == 2) {
            throw new BusinessException("该座位维护中，暂不可预约");
        }
        // 校验同一时间段是否已被预约
        Long count = reservationMapper.selectCount(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getSeatId, dto.getSeatId())
                        .eq(Reservation::getReserveDate, dto.getReserveDate())
                        .eq(Reservation::getTimeSlot, dto.getTimeSlot())
                        .in(Reservation::getStatus, 0, 1));
        if (count > 0) {
            throw new BusinessException("该座位在此时间段已被预约");
        }
        // 校验用户同一时间段是否已有预约
        Long userCount = reservationMapper.selectCount(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getUserId, userId)
                        .eq(Reservation::getReserveDate, dto.getReserveDate())
                        .eq(Reservation::getTimeSlot, dto.getTimeSlot())
                        .in(Reservation::getStatus, 0, 1));
        if (userCount > 0) {
            throw new BusinessException("您在此时间段已有预约");
        }
        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setSeatId(dto.getSeatId());
        reservation.setReserveDate(dto.getReserveDate());
        reservation.setTimeSlot(dto.getTimeSlot());
        reservation.setStatus(0);
        reservationMapper.insert(reservation);
        // 更新座位状态为已预约
        seat.setStatus(1);
        seatMapper.updateById(seat);
        log.info("用户{}预约座位{}, 日期{}, 时段{}", userId, seat.getSeatNo(), dto.getReserveDate(), dto.getTimeSlot());
    }

    @Override
    public Page<Map<String, Object>> myReservations(Long userId, Integer page, Integer size) {
        Page<Reservation> rPage = reservationMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getUserId, userId)
                        .orderByDesc(Reservation::getCreateTime));
        Page<Map<String, Object>> resultPage = new Page<>(rPage.getCurrent(), rPage.getSize(), rPage.getTotal());
        resultPage.setRecords(rPage.getRecords().stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("seatId", r.getSeatId());
            map.put("reserveDate", r.getReserveDate());
            map.put("timeSlot", r.getTimeSlot());
            map.put("status", r.getStatus());
            map.put("checkInTime", r.getCheckInTime());
            map.put("createTime", r.getCreateTime());
            Seat seat = seatMapper.selectById(r.getSeatId());
            if (seat != null) {
                map.put("seatNo", seat.getSeatNo());
                map.put("area", seat.getArea());
                map.put("floor", seat.getFloor());
                map.put("hasCharger", seat.getHasCharger());
                map.put("nearWindow", seat.getNearWindow());
            }
            return map;
        }).collect(Collectors.toList()));
        return resultPage;
    }

    @Override
    @Transactional
    public void cancelReservation(Long userId, Long id) {
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new BusinessException("预约记录不存在");
        }
        if (!reservation.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此预约");
        }
        if (reservation.getStatus() != 0) {
            throw new BusinessException("当前状态不可取消");
        }
        reservation.setStatus(3);
        reservationMapper.updateById(reservation);
        // 恢复座位状态为可用
        Seat seat = seatMapper.selectById(reservation.getSeatId());
        if (seat != null) {
            seat.setStatus(0);
            seatMapper.updateById(seat);
        }
        log.info("用户{}取消预约{}", userId, id);
    }

    @Override
    @Transactional
    public void checkIn(Long userId, Long id) {
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new BusinessException("预约记录不存在");
        }
        if (!reservation.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此预约");
        }
        if (reservation.getStatus() != 0) {
            throw new BusinessException("当前状态不可签到");
        }

        // 校验签到时间：只允许在预约时间段开始前15分钟到结束时间之间签到
        LocalDate today = LocalDate.now();
        LocalDate reserveDate = reservation.getReserveDate();
        String timeSlot = reservation.getTimeSlot();

        if (reserveDate.isAfter(today)) {
            throw new BusinessException("预约日期还未到，无法签到");
        }
        if (reserveDate.isBefore(today)) {
            throw new BusinessException("预约日期已过，无法签到");
        }

        // 解析时间段（格式如 "08:00-10:00"）
        if (timeSlot != null && timeSlot.contains("-")) {
            String[] parts = timeSlot.split("-");
            LocalTime slotStart = LocalTime.parse(parts[0].trim());
            LocalTime slotEnd = LocalTime.parse(parts[1].trim());
            LocalTime now = LocalTime.now();

            // 允许提前15分钟签到
            LocalTime earliestCheckIn = slotStart.minusMinutes(15);
            if (now.isBefore(earliestCheckIn)) {
                throw new BusinessException("签到时间未到，最早可在 " + earliestCheckIn + " 签到（预约时段 " + timeSlot + "）");
            }
            if (now.isAfter(slotEnd)) {
                throw new BusinessException("预约时间段已结束（" + timeSlot + "），无法签到");
            }
        }

        reservation.setStatus(1);
        reservation.setCheckInTime(LocalDateTime.now());
        reservationMapper.updateById(reservation);
        log.info("用户{}签到预约{}", userId, id);
    }

    @Override
    public Page<Map<String, Object>> allReservations(Integer page, Integer size, Integer status) {
        LambdaQueryWrapper<Reservation> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Reservation::getStatus, status);
        }
        wrapper.orderByDesc(Reservation::getCreateTime);
        Page<Reservation> rPage = reservationMapper.selectPage(new Page<>(page, size), wrapper);
        Page<Map<String, Object>> resultPage = new Page<>(rPage.getCurrent(), rPage.getSize(), rPage.getTotal());
        resultPage.setRecords(rPage.getRecords().stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("userId", r.getUserId());
            map.put("seatId", r.getSeatId());
            map.put("reserveDate", r.getReserveDate());
            map.put("timeSlot", r.getTimeSlot());
            map.put("status", r.getStatus());
            map.put("checkInTime", r.getCheckInTime());
            map.put("createTime", r.getCreateTime());
            Seat seat = seatMapper.selectById(r.getSeatId());
            if (seat != null) {
                map.put("seatNo", seat.getSeatNo());
                map.put("area", seat.getArea());
            }
            User user = userMapper.selectById(r.getUserId());
            if (user != null) {
                map.put("username", user.getUsername());
                map.put("nickname", user.getNickname());
            }
            return map;
        }).collect(Collectors.toList()));
        return resultPage;
    }
}
