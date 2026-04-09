# 图书馆自习座位预约系统 - 技术分析文档

## 一、座位状态流转整体流程

### 1.1 状态定义

#### 座位状态 (Seat.status)
| 值 | 状态 | 说明 |
|----|------|------|
| 0 | 可用 | 座位空闲，可预约 |
| 1 | 已预约 | 已被用户预约，暂时不可被他人预约 |
| 2 | 维护中 | 座位处于维护状态，不可预约 |

#### 预约状态 (Reservation.status)
| 值 | 状态 | 说明 |
|----|------|------|
| 0 | 待使用 | 用户已预约，未签到 |
| 1 | 使用中 | 用户已签到，正在使用座位 |
| 2 | 已完成 | 用户已打卡，流程结束 |
| 3 | 已取消 | 用户主动取消预约 |
| 4 | 超时未到 | 签到超时，系统自动取消 |

### 1.2 完整状态流转图

```
用户预约座位
    ↓
座位: 可用(0) → 已预约(1)
预约: 待使用(0)
    ↓
    ├─────────────────────────────────┐
    │                             超时未到(30分钟未签到)
    ▼                                 ▼
用户签到                         预约: 待使用→超时未到(4)
预约: 待使用(0) → 使用中(1)         座位: 已预约→可用(0)
    ↓
    ├─────────────────────────────────┐
    │                             超时结束(时段结束)
    ▼                                 ▼
上传打卡照片                   预约: 使用中→已完成(2)
自动清洁度检测                   座位: 已预约→可用(0)
    ↓
    ├─────────────────────────────────┐
    │                           人工复审
    ▼                                 ▼
自动检测通过(>=60分)             管理员设置通过/不通过
预约: 使用中(1) → 已完成(2)
座位: 已预约(1) → 可用(0)
流程结束
```

### 1.3 关键业务节点时间规则

| 操作 | 时间窗口 | 说明 |
|------|----------|------|
| 取消预约 | 签到前随时 | 状态为"待使用"时可取消 |
| 签到 | 时段开始前15分钟 至 时段结束 | 例：时段08:00-10:00 → 07:45 后可签到 |
| 超时判定 | 时段开始后30分钟 | 仍未签到则标记为超时 |
| 预约时段 | 当天/未来 | 已完全过去的时段不可预约 |

---

## 二、代码实现层面分析

### 2.1 后端技术栈
| 技术 | 用途 |
|------|------|
| Spring Boot 3.x | 后端框架 |
| MyBatis Plus | ORM框架 |
| JWT | 身份认证 |
| MySQL 8.0 | 数据存储 |
| Spring Scheduler | 定时任务 |
| Java AWT ImageIO | 图像清洁度检测 |

### 2.2 核心模块实现

#### 1) 预约创建 - ReservationServiceImpl.createReservation()
**位置**: `backend/src/main/java/com/library/seat/service/impl/ReservationServiceImpl.java:37`

**执行流程**：
1. **日期校验**：预约日期不能早于今天
2. **时段校验**：当天已完全过去的时间段不可预约
3. **座位存在性校验**：检查座位是否存在
4. **维护状态校验**：维护中座位不可预约
5. **重复预约校验**：同一时间段座位已被预约则抛出异常
6. **用户冲突校验**：用户同一时间段不能有多个预约
7. **数据持久化**：创建预约记录(status=0)，更新座位状态(status=1)

**关键代码**：
```java
// 校验同一时间段是否已被预约
Long count = reservationMapper.selectCount(
    new LambdaQueryWrapper<Reservation>()
        .eq(Reservation::getSeatId, dto.getSeatId())
        .eq(Reservation::getReserveDate, dto.getReserveDate())
        .eq(Reservation::getTimeSlot, dto.getTimeSlot())
        .in(Reservation::getStatus, 0, 1));
```

#### 2) 签到功能 - ReservationServiceImpl.checkIn()
**位置**: `backend/src/main/java/com/library/seat/service/impl/ReservationServiceImpl.java:150`

**执行流程**：
1. 预约存在性校验 + 权限校验（只能操作自己的预约）
2. 状态校验：必须是"待使用"状态才能签到
3. 日期校验：必须在预约日期当天
4. 时间窗口校验：时段开始前15分钟到结束时间之间
5. 更新预约状态为：待使用(0) → 使用中(1)

#### 3) 超时处理定时任务 - ReservationTimeoutTask
**位置**: `backend/src/main/java/com/library/seat/task/ReservationTimeoutTask.java:37`

**调度策略**：每分钟执行一次 (`@Scheduled(fixedRate = 60000)`)

**处理逻辑**：
1. 遍历所有状态为"待使用(0)"的预约
2. 预约日期已过 → 直接标记超时
3. 当天预约且时段开始超过30分钟未签到 → 标记超时
4. 超时后：预约设为status=4，座位状态释放为可用

#### 4) 清洁度检测服务 - CleanDetectionService
**位置**: `backend/src/main/java/com/library/seat/service/CleanDetectionService.java:34`

**检测算法（4项指标）**：

| 指标 | 权重 | 计算方式 |
|------|------|----------|
| 亮度评分 | 25% | 干净桌面通常亮度均匀且较高(>=120满分) |
| 均匀度评分 | 25% | 亮度标准差小则均匀 (<=35满分) |
| 暗色占比评分 | 25% | 暗色像素占比 <=15% 满分 |
| 区域一致性评分 | 25% | 8x8网格块亮度差异小 |

**判定阈值**：综合得分 >= 60 → 清洁通过

**降级策略**：Docker headless环境下自动切换为字节采样备用算法

---

## 三、设计模式与架构思想

### 3.1 权限校验架构

#### 1) 认证机制 - JWT拦截器

**实现位置**：`JwtInterceptor.java:19`

**架构层次**：
```
客户端请求
    ↓ (Header: Authorization: Bearer {token})
HandlerInterceptor.preHandle()
    ├─ OPTIONS请求直接放行
    ├─ Token格式校验
    ├─ Token过期校验
    └─ Token解析 → userId/username/role存入request属性
    ↓
Controller层
    ↓
Service层（通过@RequestAttribute获取）
```

#### 2) 授权校验

**角色定义**（User.role）：
- **0 - 普通用户**：座位浏览/预约/签到/打卡/取消
- **1 - 管理员**：用户管理/座位管理/预约管理/审核/日志查看

**校验方式 - 服务层方法级校验**：
```java
// ReservationServiceImpl.checkIn() - 操作权校验
if (!reservation.getUserId().equals(userId)) {
    throw new BusinessException("无权操作此预约");
}
```

### 3.2 设计模式应用

| 模式 | 应用场景 | 优点 |
|------|----------|------|
| **拦截器模式** | JWT身份认证、操作日志AOP | 横切关注点分离，统一处理 |
| **AOP切面** | `OperationLogAspect.java` - 日志记录 | 业务代码与日志解耦 |
| **依赖注入** | Service层@RequiredArgsConstructor + @Service | 松耦合、易测试 |
| **策略模式** | CleanDetectionService - 主/备检测算法切换 | 运行时动态选择、容错 |
| **状态机** | Reservation/Seat状态流转控制 | 状态变更集中管理、边界清晰 |
| **DTO模式** | LoginDTO/ReservationDTO等 | 数据隔离、防止敏感字段泄漏 |
| **定时任务模式** | ReservationTimeoutTask超时处理 | 异步解耦、集中调度 |

### 3.3 架构设计原则

1. **单一职责原则 (SRP)**：
   - ReservationService：预约创建/取消/签到
   - CheckRecordService：打卡/清洁度审核
   - CleanDetectionService：仅负责图像算法
   - JwtInterceptor：仅负责认证

2. **开闭原则 (OCP)**：
   - 新状态可通过扩展status枚举值添加
   - 新校验规则可通过添加Service层方法实现

3. **错误处理**：
   - 自定义`BusinessException` + `@RestControllerAdvice`全局异常捕获
   - 服务层参数校验 → 异常抛出 → 全局拦截 → 标准化JSON返回

---

## 四、系统整体架构图

### 4.1 部署架构 (docker-compose.yml)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                 客户端浏览器                                 │
└───────────────────┬─────────────────────────────────────────────────────────┘
                    │
                    │ 8081
                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Frontend Admin (Vue3)                               │
│  container: seat-frontend-admin                                             │
│  ports: 8081:80                                                             │
└───────────────────┬─────────────────────────────────────────────────────────┘
                    │
                    │ 8088 /api
                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Backend (Spring Boot)                            │
│  container: seat-backend                                                    │
│  ports: 8088:8088                                                           │
│  modules: Controller / Service / Mapper / AOP / Scheduler                   │
│  services:                                                                  │
│    ├─ ReservationService   - 预约状态流转管理                               │
│    ├─ CheckRecordService   - 打卡记录管理                                   │
│    ├─ SeatService          - 座位CRUD                                       │
│    ├─ CleanDetectionService - 图像清洁度检测                                │
│    └─ ReservationTimeoutTask - 超时处理定时任务                             │
└───────────────────┬─────────────────────────────────────────────────────────┘
                    │ 3306 JDBC
                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              MySQL 8.0 容器                                  │
│  container: seat-mysql    port: 3307:3306                                   │
│  database: seat_reservation                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│  Tables:                                                                    │
│  ┌──────────────┐  ┌──────────────────┐  ┌────────────────────┐             │
│  │   sys_user   │  │    seat          │  │ reservation        │             │
│  │ - id         │  │  - id            │  │  - id              │             │
│  │ - username   │  │  - seat_no       │  │  - user_id         │             │
│  │ - password   │  │  - status {0,1,2}│  │  - seat_id         │             │
│  │ - role {0,1} │  │  - area          │  │  - time_slot       │             │
│  └──────────────┘  └──────────────────┘  │  - status {0,1,2,3,4}            │
│  ┌──────────────────┐  ┌──────────────┐  └────────────────────┘             │
│  │ check_record     │  │operation_log │                                     │
│  │ - reservation_id │  │ - module     │                                     │
│  │ - auto_score     │  │ - action     │                                     │
│  │ - clean_passed   │  │ - user_id    │                                     │
│  └──────────────────┘  └──────────────┘                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 层次架构 (MVC)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Presentation Layer                                 │
│  Vue3 + Vite 前端                                                           │
│  Views: Login / SeatList / MyReservations                                   │
│         Admin: User/Seat/Reservation/CheckRecord/Log                        │
└─────────────────────────────────────────────────────────────────────────────┘
                              ↓ HTTP / JSON
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Controller Layer                                   │
│  @RestController                                                            │
│  AuthController / SeatController / ReservationController                    │
│  CheckRecordController / UserController / OperationLogController            │
└─────────────────────────────────────────────────────────────────────────────┘
                              ↓ 调用 + 权限判断
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Service Layer                                    │
│  @Service + 事务 @Transactional                                             │
│  ReservationServiceImpl / CheckRecordServiceImpl                            │
│    + ReservationTimeoutTask (Scheduled)                                     │
│    + CleanDetectionService 【核心算法模块】                                  │
└─────────────────────────────────────────────────────────────────────────────┘
                              ↓ MyBatis Plus
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Mapper Layer                                   │
│  BaseMapper<T> / LambdaQueryWrapper                                         │
└─────────────────────────────────────────────────────────────────────────────┘
                              ↓ JDBC
┌─────────────────────────────────────────────────────────────────────────────┐
│                             Database Layer                                  │
│  MySQL 8.0 - 支持事务 + 行级锁                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.3 跨模块协作时序图：预约完整流程

```
用户         前端         AuthContr        ReservationServ       DB
 │            │             │                 │                │
 │ 登录请求    │             │                 │                │
 ├───────────►│ POST /login │                 │                │
 │            ├────────────►│ 校验 + JWT生成   │                │
 │            │             ├────────────────►│ 查询User        │
 │            │             │◄────────────────┤ 返回User + 密码校验
 │ 接收Token   │◄────────────┤ 返回{token}      │                │
 │◄───────────┤             │                 │                │
 │            │             │                 │                │
 │ 预约请求    │ 携带Token    │                 │                │
 ├───────────►│ POST /api/reservation         │                │
 │            ├────────────►│JWT拦截器        │                │
 │            │             │ 校验Token有效   │                │
 │            │             ├────────────────►│ 校验日期/时段   │
 │            │             │                 │ 座位状态校验    │
 │            │             │                 ├───────────────►│ SELECT seat
 │            │             │                 │◄───────────────┤ 返回座位 status=0
 │            │             │                 │                │
 │            │             │                 │ 冲突校验 + 落库 │
 │            │             │                 │ INSERT reservation status=0
 │            │             │                 │ UPDATE seat status=1
 │            │             │                 ├───────────────►│
 │            │             │                 │◄───────────────┤ 事务提交
 │ 预约成功    │◄────────────┤◄────────────────┤ 返回成功        │
 │◄───────────┤             │                 │                │
```
