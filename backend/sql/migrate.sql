-- 数据库迁移脚本：确保 check_record 表包含自动清洁度检测字段
-- 此脚本可重复执行，已存在的列不会重复添加

USE seat_reservation;

-- 添加自动清洁度评分字段（如果不存在）
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='seat_reservation' AND TABLE_NAME='check_record' AND COLUMN_NAME='auto_clean_score');
SET @sql := IF(@exist = 0, 'ALTER TABLE check_record ADD COLUMN auto_clean_score INT DEFAULT 0 COMMENT "自动清洁度评分(0-100)" AFTER clean_passed', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加自动检测结果字段（如果不存在）
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='seat_reservation' AND TABLE_NAME='check_record' AND COLUMN_NAME='auto_clean_result');
SET @sql := IF(@exist = 0, 'ALTER TABLE check_record ADD COLUMN auto_clean_result TINYINT DEFAULT 0 COMMENT "自动检测结果: 0-未检测 1-清洁 2-不清洁" AFTER auto_clean_score', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加自动检测详情字段（如果不存在）
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='seat_reservation' AND TABLE_NAME='check_record' AND COLUMN_NAME='auto_clean_detail');
SET @sql := IF(@exist = 0, 'ALTER TABLE check_record ADD COLUMN auto_clean_detail VARCHAR(500) COMMENT "自动检测详情说明" AFTER auto_clean_result', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
