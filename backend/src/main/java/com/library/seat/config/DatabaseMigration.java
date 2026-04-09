package com.library.seat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据库迁移：应用启动时自动检查并添加缺失的列
 */
@Slf4j
@Component
public class DatabaseMigration implements CommandLineRunner {

    private final DataSource dataSource;

    public DatabaseMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            // 检查 check_record 表是否存在 auto_clean_score 列
            if (!columnExists(conn, "check_record", "auto_clean_score")) {
                stmt.execute("ALTER TABLE check_record ADD COLUMN auto_clean_score INT DEFAULT 0 COMMENT '自动清洁度评分(0-100)' AFTER clean_passed");
                log.info("数据库迁移: 添加 check_record.auto_clean_score 列");
            }
            if (!columnExists(conn, "check_record", "auto_clean_result")) {
                stmt.execute("ALTER TABLE check_record ADD COLUMN auto_clean_result TINYINT DEFAULT 0 COMMENT '自动检测结果: 0-未检测 1-清洁 2-不清洁' AFTER auto_clean_score");
                log.info("数据库迁移: 添加 check_record.auto_clean_result 列");
            }
            if (!columnExists(conn, "check_record", "auto_clean_detail")) {
                stmt.execute("ALTER TABLE check_record ADD COLUMN auto_clean_detail VARCHAR(500) COMMENT '自动检测详情说明' AFTER auto_clean_result");
                log.info("数据库迁移: 添加 check_record.auto_clean_detail 列");
            }
            log.info("数据库迁移检查完成");
        } catch (Exception e) {
            log.warn("数据库迁移检查失败（表可能尚未创建，将在首次初始化时自动包含这些列）: {}", e.getMessage());
        }
    }

    private boolean columnExists(Connection conn, String table, String column) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }
}
