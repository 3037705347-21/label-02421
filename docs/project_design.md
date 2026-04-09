# 图书馆自习座位预约系统 - 项目设计文档

## 1. 系统架构

```mermaid
flowchart TD
    subgraph Frontend["前端 (Vue 3 + Element Plus)"]
        A[用户端页面] --> A1[登录/注册]
        A --> A2[座位地图/列表]
        A --> A3[我的预约]
        A --> A4[拍照打卡]
        B[管理端页面] --> B1[座位管理]
        B --> B2[预约管理]
        B --> B3[用户管理]
        B --> B4[操作日志]
    end

    subgraph Backend["后端 (Spring Boot 3)"]
        C[Controller层] --> D[Service层]
        D --> E[Mapper层]
        E --> F[(MySQL 8.0)]
        G[JWT认证过滤器] --> C
        H[全局异常处理] --> C
        I[操作日志AOP] --> D
    end

    Frontend -->|Axios HTTP| Backend
```

## 2. ER 图

```mermaid
erDiagram
    USER ||--o{ RESERVATION : creates
    SEAT ||--o{ RESERVATION : has
    USER ||--o{ OPERATION_LOG : generates
    RESERVATION ||--o| CHECK_RECORD : has

    USER {
        bigint id PK
        varchar username
        varchar password
        varchar nickname
        varchar phone
        tinyint role "0-普通用户 1-管理员"
        datetime create_time
        datetime update_time
        tinyint deleted
    }

    SEAT {
        bigint id PK
        varchar seat_no "座位编号"
        tinyint has_charger "是否有充电孔"
        tinyint near_window "是否靠窗"
        tinyint status "0-可用 1-已预约 2-维护中"
        varchar area "区域"
        int floor "楼层"
        datetime create_time
        datetime update_time
        tinyint deleted
    }

    RESERVATION {
        bigint id PK
        bigint user_id FK
        bigint seat_id FK
        date reserve_date "预约日期"
        varchar time_slot "时间段"
        tinyint status "0-待使用 1-使用中 2-已完成 3-已取消 4-超时未到"
        datetime check_in_time "签到时间"
        datetime create_time
        datetime update_time
        tinyint deleted
    }

    CHECK_RECORD {
        bigint id PK
        bigint reservation_id FK
        bigint user_id FK
        varchar photo_url "打卡照片URL"
        tinyint clean_passed "清洁是否通过"
        varchar remark "备注"
        datetime create_time
    }

    OPERATION_LOG {
        bigint id PK
        bigint user_id FK
        varchar module "操作模块"
        varchar action "操作类型"
        varchar detail "操作详情"
        varchar ip "IP地址"
        datetime create_time
    }
```

## 3. 接口清单

### AuthController - 认证模块
| Method | Path | Description |
|--------|------|-------------|
| POST | /api/auth/login | 用户登录 |
| POST | /api/auth/register | 用户注册 |
| GET | /api/auth/info | 获取当前用户信息 |

### SeatController - 座位模块
| Method | Path | Description |
|--------|------|-------------|
| GET | /api/seat/list | 座位列表(支持筛选) |
| POST | /api/seat | 新增座位(管理员) |
| PUT | /api/seat | 修改座位(管理员) |
| DELETE | /api/seat/{id} | 删除座位(管理员) |

### ReservationController - 预约模块
| Method | Path | Description |
|--------|------|-------------|
| POST | /api/reservation | 创建预约 |
| GET | /api/reservation/my | 我的预约列表 |
| PUT | /api/reservation/cancel/{id} | 取消预约 |
| PUT | /api/reservation/checkin/{id} | 签到 |
| GET | /api/reservation/list | 所有预约(管理员) |

### CheckRecordController - 打卡模块
| Method | Path | Description |
|--------|------|-------------|
| POST | /api/check/upload | 上传清洁打卡照片 |
| GET | /api/check/{reservationId} | 查看打卡记录 |
| GET | /api/check/list | 打卡记录列表(管理员) |

### UserController - 用户管理模块
| Method | Path | Description |
|--------|------|-------------|
| GET | /api/user/list | 用户列表(管理员) |
| PUT | /api/user/status/{id} | 启用/禁用用户(管理员) |

### OperationLogController - 日志模块
| Method | Path | Description |
|--------|------|-------------|
| GET | /api/log/list | 操作日志列表(管理员) |

## 4. UI/UX 规范

- **主色调**: `#409EFF` (Element Plus 蓝)
- **辅助色**: `#67C23A` (成功绿), `#E6A23C` (警告橙), `#F56C6C` (危险红)
- **背景色**: `#F5F7FA` (页面背景), `#FFFFFF` (卡片背景)
- **字体**: `"Helvetica Neue", Helvetica, "PingFang SC", "Microsoft YaHei", sans-serif`
- **字号**: 标题 `20px`, 正文 `14px`, 辅助文字 `12px`
- **卡片圆角**: `8px`
- **卡片阴影**: `0 2px 12px rgba(0, 0, 0, 0.08)`
- **间距体系**: `8px / 16px / 24px / 32px`
- **按钮圆角**: `4px`
