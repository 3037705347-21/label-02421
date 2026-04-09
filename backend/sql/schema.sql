-- 图书馆自习座位预约系统 数据库初始化脚本
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS seat_reservation DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE seat_reservation;

-- 用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    nickname VARCHAR(50) COMMENT '昵称',
    phone VARCHAR(20) COMMENT '手机号',
    role TINYINT DEFAULT 0 COMMENT '角色: 0-普通用户 1-管理员',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 座位表
DROP TABLE IF EXISTS seat;
CREATE TABLE seat (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    seat_no VARCHAR(20) NOT NULL COMMENT '座位编号',
    has_charger TINYINT DEFAULT 0 COMMENT '是否有充电孔: 0-无 1-有',
    near_window TINYINT DEFAULT 0 COMMENT '是否靠窗: 0-否 1-是',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-可用 1-已预约 2-维护中',
    area VARCHAR(50) COMMENT '区域',
    floor INT DEFAULT 1 COMMENT '楼层',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='座位表';

-- 预约表
DROP TABLE IF EXISTS reservation;
CREATE TABLE reservation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    seat_id BIGINT NOT NULL COMMENT '座位ID',
    reserve_date DATE NOT NULL COMMENT '预约日期',
    time_slot VARCHAR(30) NOT NULL COMMENT '时间段',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-待使用 1-使用中 2-已完成 3-已取消 4-超时未到',
    check_in_time DATETIME COMMENT '签到时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    INDEX idx_user_id (user_id),
    INDEX idx_seat_id (seat_id),
    INDEX idx_reserve_date (reserve_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预约表';

-- 打卡记录表
DROP TABLE IF EXISTS check_record;
CREATE TABLE check_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    reservation_id BIGINT NOT NULL COMMENT '预约ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    photo_url VARCHAR(500) COMMENT '打卡照片URL',
    clean_passed TINYINT DEFAULT 0 COMMENT '最终审核状态: 0-待审核 1-通过 2-未通过',
    auto_clean_score INT DEFAULT 0 COMMENT '自动清洁度评分(0-100)',
    auto_clean_result TINYINT DEFAULT 0 COMMENT '自动检测结果: 0-未检测 1-清洁 2-不清洁',
    auto_clean_detail VARCHAR(500) COMMENT '自动检测详情说明',
    remark VARCHAR(255) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_reservation_id (reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='打卡记录表';

-- 操作日志表
DROP TABLE IF EXISTS operation_log;
CREATE TABLE operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT COMMENT '操作用户ID',
    module VARCHAR(50) COMMENT '操作模块',
    action VARCHAR(50) COMMENT '操作类型',
    detail VARCHAR(500) COMMENT '操作详情',
    ip VARCHAR(50) COMMENT 'IP地址',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- ========== 初始数据 ==========

-- 管理员账号 admin/123456
INSERT INTO sys_user (username, password, nickname, phone, role) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', '13800000000', 1);

-- 示例座位数据
INSERT INTO seat (seat_no, has_charger, near_window, status, area, floor) VALUES
('A-001', 1, 1, 0, 'A区', 1),
('A-002', 1, 0, 0, 'A区', 1),
('A-003', 0, 1, 0, 'A区', 1),
('A-004', 0, 0, 0, 'A区', 1),
('A-005', 1, 1, 0, 'A区', 1),
('B-001', 1, 0, 0, 'B区', 2),
('B-002', 0, 1, 0, 'B区', 2),
('B-003', 1, 1, 0, 'B区', 2),
('B-004', 0, 0, 0, 'B区', 2),
('B-005', 1, 0, 0, 'B区', 2);
