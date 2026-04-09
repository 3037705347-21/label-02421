package com.library.seat.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.library.seat.entity.Reservation;
import com.library.seat.entity.Seat;
import com.library.seat.mapper.ReservationMapper;
import com.library.seat.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 预约超时自动处理定时任务
 * 每分钟扫描一次，将已过时间段开始时间但仍未签到的预约标记为"超时未到"，并释放座位
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTimeoutTask {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;

    /**
     * 每分钟执行一次
     * 规则：预约日期为今天且时间段开始时间已过30分钟仍未签到 → 标记为超时未到(4)
     *       预约日期在今天之前且状态仍为待使用(0) → 直接标记为超时未到(4)
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkTimeoutReservations() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // 查询所有"待使用"状态的预约
        List<Reservation> pendingList = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getStatus, 0)
                        .eq(Reservation::getDeleted, 0)
        );

        if (pendingList.isEmpty()) {
            return;
        }

        int timeoutCount = 0;
        for (Reservation reservation : pendingList) {
            boolean shouldTimeout = false;

            if (reservation.getReserveDate().isBefore(today)) {
                // 预约日期已过，直接超时
                shouldTimeout = true;
            } else if (reservation.getReserveDate().isEqual(today)) {
                // 当天预约，检查时间段是否已过开始时间30分钟
                String timeSlot = reservation.getTimeSlot();
                if (timeSlot != null && timeSlot.contains("-")) {
                    try {
                        String startTimeStr = timeSlot.split("-")[0].trim();
                        LocalTime slotStart = LocalTime.parse(startTimeStr);
                        // 超过开始时间30分钟未签到则判定超时
                        if (now.isAfter(slotStart.plusMinutes(30))) {
                            shouldTimeout = true;
                        }
                    } catch (Exception e) {
                        log.warn("解析时间段失败: reservationId={}, timeSlot={}", reservation.getId(), timeSlot);
                    }
                }
            }

            if (shouldTimeout) {
                reservation.setStatus(4);
                reservationMapper.updateById(reservation);
                // 释放座位
                Seat seat = seatMapper.selectById(reservation.getSeatId());
                if (seat != null && seat.getStatus() == 1) {
                    seat.setStatus(0);
                    seatMapper.updateById(seat);
                }
                timeoutCount++;
                log.info("预约超时自动取消: reservationId={}, seatId={}, date={}, timeSlot={}",
                        reservation.getId(), reservation.getSeatId(),
                        reservation.getReserveDate(), reservation.getTimeSlot());
            }
        }

        if (timeoutCount > 0) {
            log.info("定时任务执行完成: 共处理{}条超时预约", timeoutCount);
        }
    }
}
