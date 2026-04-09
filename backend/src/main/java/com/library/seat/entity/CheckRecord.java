package com.library.seat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("check_record")
public class CheckRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reservationId;
    private Long userId;
    private String photoUrl;
    private Integer cleanPassed;
    @TableField(value = "auto_clean_score")
    private Integer autoCleanScore;
    @TableField(value = "auto_clean_result")
    private Integer autoCleanResult;
    @TableField(value = "auto_clean_detail")
    private String autoCleanDetail;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
