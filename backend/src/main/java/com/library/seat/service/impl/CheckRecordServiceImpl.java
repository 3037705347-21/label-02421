package com.library.seat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.seat.entity.CheckRecord;
import com.library.seat.entity.Reservation;
import com.library.seat.entity.Seat;
import com.library.seat.entity.User;
import com.library.seat.exception.BusinessException;
import com.library.seat.mapper.CheckRecordMapper;
import com.library.seat.mapper.ReservationMapper;
import com.library.seat.mapper.SeatMapper;
import com.library.seat.mapper.UserMapper;
import com.library.seat.service.CheckRecordService;
import com.library.seat.service.CleanDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckRecordServiceImpl implements CheckRecordService {

    private final CheckRecordMapper checkRecordMapper;
    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final UserMapper userMapper;
    private final CleanDetectionService cleanDetectionService;

    @Value("${file.upload-path}")
    private String uploadPath;

    // 允许的图片类型
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
    );
    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @Override
    @Transactional
    public Map<String, Object> uploadCheck(Long userId, Long reservationId, MultipartFile file, String remark) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException("预约记录不存在");
        }
        if (!reservation.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此预约");
        }
        if (reservation.getStatus() != 1) {
            throw new BusinessException("当前预约状态不可打卡，请先签到");
        }
        // 检查是否已打卡
        Long count = checkRecordMapper.selectCount(
                new LambdaQueryWrapper<CheckRecord>().eq(CheckRecord::getReservationId, reservationId));
        if (count > 0) {
            throw new BusinessException("该预约已完成打卡");
        }

        // === 文件安全校验 ===
        if (file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过5MB");
        }
        // 校验文件扩展名
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("仅支持上传图片文件（jpg/png/gif/webp/bmp）");
        }
        // 校验 Content-Type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("文件类型不合法，仅支持图片格式");
        }

        // 使用 UUID 重命名，防止路径遍历攻击
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        // 使用绝对路径，避免相对路径在不同运行环境下的问题
        Path dirPath = Path.of(uploadPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", dirPath, e);
            throw new BusinessException("文件上传失败");
        }
        File destFile = dirPath.resolve(fileName).toFile();
        try {
            // 使用 InputStream + Files.copy 代替 transferTo，避免临时文件被清理导致的异常
            Files.copy(file.getInputStream(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("文件上传失败, 目标路径: {}, 错误: ", destFile.getAbsolutePath(), e);
            throw new BusinessException("文件上传失败");
        }
        // 保存打卡记录
        CheckRecord record = new CheckRecord();
        record.setReservationId(reservationId);
        record.setUserId(userId);
        record.setPhotoUrl("/uploads/" + fileName);
        record.setRemark(remark);

        // 自动清洁度检测
        Map<String, Object> detection = cleanDetectionService.analyzeCleanness(destFile);
        int autoScore = detection.get("score") instanceof Number ? ((Number) detection.get("score")).intValue() : 0;
        int autoResult = detection.get("result") instanceof Number ? ((Number) detection.get("result")).intValue() : 0;
        String autoDetail = detection.get("detail") != null ? detection.get("detail").toString() : "";
        record.setAutoCleanScore(autoScore);
        record.setAutoCleanResult(autoResult);
        record.setAutoCleanDetail(autoDetail);

        // 自动检测通过（评分>=60）则直接标记通过，否则待人工复审
        if (autoResult == 1) {
            record.setCleanPassed(1);
            log.info("用户{}打卡自动检测通过, 评分: {}, 预约ID: {}", userId, autoScore, reservationId);
        } else {
            record.setCleanPassed(0);
            log.info("用户{}打卡自动检测未通过, 评分: {}, 需人工复审, 预约ID: {}", userId, autoScore, reservationId);
        }

        checkRecordMapper.insert(record);
        // 更新预约状态为已完成
        reservation.setStatus(2);
        reservationMapper.updateById(reservation);
        // 恢复座位状态为可用
        Seat seat = seatMapper.selectById(reservation.getSeatId());
        if (seat != null) {
            seat.setStatus(0);
            seatMapper.updateById(seat);
        }
        log.info("用户{}完成打卡, 预约ID: {}", userId, reservationId);

        // 返回检测结果给前端
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("autoCleanScore", autoScore);
        resultMap.put("autoCleanResult", autoResult);
        resultMap.put("autoCleanDetail", autoDetail);
        resultMap.put("cleanPassed", record.getCleanPassed());
        return resultMap;
    }

    @Override
    public CheckRecord getByReservationId(Long reservationId) {
        return checkRecordMapper.selectOne(
                new LambdaQueryWrapper<CheckRecord>().eq(CheckRecord::getReservationId, reservationId));
    }

    @Override
    public void reviewCheck(Long checkId, Integer cleanPassed) {
        CheckRecord record = checkRecordMapper.selectById(checkId);
        if (record == null) {
            throw new BusinessException("打卡记录不存在");
        }
        record.setCleanPassed(cleanPassed);
        checkRecordMapper.updateById(record);
        log.info("审核打卡记录: checkId={}, cleanPassed={}", checkId, cleanPassed);
    }

    @Override
    public Page<Map<String, Object>> listCheckRecords(Integer page, Integer size) {
        Page<CheckRecord> cPage = checkRecordMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<CheckRecord>().orderByDesc(CheckRecord::getCreateTime));
        Page<Map<String, Object>> resultPage = new Page<>(cPage.getCurrent(), cPage.getSize(), cPage.getTotal());
        resultPage.setRecords(cPage.getRecords().stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("reservationId", c.getReservationId());
            map.put("userId", c.getUserId());
            map.put("photoUrl", c.getPhotoUrl());
            map.put("cleanPassed", c.getCleanPassed());
            map.put("autoCleanScore", c.getAutoCleanScore());
            map.put("autoCleanResult", c.getAutoCleanResult());
            map.put("autoCleanDetail", c.getAutoCleanDetail());
            map.put("remark", c.getRemark());
            map.put("createTime", c.getCreateTime());
            User user = userMapper.selectById(c.getUserId());
            if (user != null) {
                map.put("username", user.getUsername());
                map.put("nickname", user.getNickname());
            }
            return map;
        }).collect(Collectors.toList()));
        return resultPage;
    }
}
