package com.library.seat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.seat.entity.CheckRecord;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface CheckRecordService {
    Map<String, Object> uploadCheck(Long userId, Long reservationId, MultipartFile file, String remark);
    CheckRecord getByReservationId(Long reservationId);
    Page<Map<String, Object>> listCheckRecords(Integer page, Integer size);
    void reviewCheck(Long checkId, Integer cleanPassed);
}
