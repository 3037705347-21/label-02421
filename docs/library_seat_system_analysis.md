# 图书馆座位预约系统分析报告

## 一、座位状态流转业务逻辑

### 1.1 座位状态定义

| 状态值 | 状态说明 |
|-------|----------|
| 0 | 可用 |
| 1 | 已预约 |
| 2 | 维护中 |

### 1.2 预约状态定义

| 状态值 | 状态说明 |
|-------|----------|
| 0 | 待使用 |
| 1 | 使用中 |
| 2 | 已完成 |
| 3 | 已取消 |
| 4 | 超时未到 |

### 1.3 状态流转图

```
座位状态流转：
┌─────────┐     ┌──────────┐     ┌──────────┐
│  可用   │────▶│ 已预约  │────▶│ 使用中  │
│ (status=0) │     │ (status=1) │     │          │
└─────────┘     └──────────┘     └──────────┘
     │                │                │
     │                │                ▼
     │                │           释放座位
     │                │          回到可用(0)
     │                │
     │           ┌────▼────┐
     │           │ 取消预约│
     │           │ 或超时  │
     │           └────┬────┘
     │                │
     └────────────────┘
           回到可用(0)
```

## 二、代码实现逻辑

### 2.1 核心服务实现 (`ReservationServiceImpl.java:36-92`)

**创建预约：**
1. 日期校验：预约日期不能早于今天
2. 时间段校验：当天已完全过去的时间段不可预约
3. 座位存在校验：座位必须存在且不是维护中
4. 互斥校验：同一时间段座位不可重复预约，用户同一时间段不可重复预约
5. 创建预约记录(status=0)，更新座位状态为已预约(status=1)

**取消预约：** (`ReservationServiceImpl.java:126-146`)
1. 校验预约存在且属于当前用户
2. 校验状态为"待使用"才可取消
3. 更新预约状态为"已取消"(status=3)
4. 恢复座位状态为"可用"(status=0)

**签到功能：** (`ReservationServiceImpl.java:150-195`)
1. 校验预约存在且属于当前用户
2. 校验状态为"待使用"
3. 时间校验：只允许在时段开始前15分钟到结束时间之间签到
4. 更新预约状态为"使用中"(status=1)

### 2.2 超时定时任务 (`ReservationTimeoutTask.java:35-95`)

- **执行频率**：每分钟执行一次(`@Scheduled(fixedRate = 60000)`)
- **超时规则**：
  - 预约日期已过，状态仍为"待使用" → 标记为超时
  - 当天预约，超过时段开始时间30分钟未签到 → 标记为超时
- **处理动作**：更新预约status=4，释放座位回到可用status=0

## 三、权限校验与设计模式

### 3.1 权限校验实现

**JWT拦截器模式** (`JwtInterceptor.java:19-49`)
- 实现 `HandlerInterceptor` 接口，采用**拦截器模式**
- 在 `preHandle` 方法中：
  1. 从Header获取Authorization token
  2. 校验token格式、有效性、是否过期
  3. 解析token存入userId、username、role到request属性

**配置类** (`WebMvcConfig.java`)
- 注册拦截器，配置白名单路径(登录、注册等无需认证)

### 3.2 设计模式应用

1. **拦截器模式(Interceptor Pattern)**
   - JwtInterceptor 实现统一的身份认证
   - 无需在每个Controller中重复写认证逻辑

2. **面向切面编程(AOP)** (`OperationLogAspect.java:18-66`)
   - `@Aspect` + `@Around` 实现操作日志记录
   - 自定义注解 `@LogOperation` 标记需要日志的方法
   - 解耦业务逻辑与横切关注点(日志)

3. **依赖注入(DI)与控制反转(IoC)**
   - `@RequiredArgsConstructor` (Lombok) 实现构造注入
   - 服务间依赖松耦合，便于单元测试

4. **Mapper模式(MyBatis-Plus)**
   - 继承BaseMapper实现CRUD
   - LambdaQueryWrapper 类型安全的查询构建

5. **状态机思想(State Machine)**
   - 预约和座位有明确的状态转换规则
   - 每个操作前校验当前状态是否合法
   - 事务保证状态转换的原子性(`@Transactional`)

6. **定时任务模式(Scheduled Task)**
   - `@Scheduled` 处理超时这种异步的状态更新

### 3.3 架构分层(经典MVC)

```
┌─────────────────────────────────────────────────────────┐
│                    Controller层                         │
│  AuthController / SeatController / ReservationController │
│     接收请求 → 参数校验 → 调用Service → 返回Result       │
└─────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────┐
│                      Service层                          │
│   AuthService / SeatService / ReservationService        │
│       业务逻辑 → 状态校验 → 事务控制 → Mapper调用        │
└─────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────┐
│                       Mapper层                          │
│   SeatMapper / ReservationMapper / UserMapper           │
│           MyBatis-Plus BaseMapper → 数据库IO           │
└─────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────┐
│                      Entity层                           │
│   User / Seat / Reservation / CheckRecord               │
│        与表一一对应 → 数据模型 → 表字段映射             │
└─────────────────────────────────────────────────────────┘
```

## 四、全系统架构图

### 4.1 系统拓扑图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            图书馆座位预约系统 架构                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  [前端层]                          [后端层]                          [数据层] │
│                                                                             │
│  frontend-admin/                SpringBoot 3.x                     MySQL 8.x│
│  ┌──────────────────────┐       ┌──────────────────────────────┐       │
│  │  Vue 3 + Vite        │       │  Controller  @RestController  │       │
│  │  Element Plus        │───JWT──▶  Interceptor + AOP Aspect    │◀──JDBC──▶   │
│  │  Vue Router + Pinia  │       │  Service + Scheduled Tasks    │       │
│  │                      │       │  MyBatis-Plus Mapper          │       │
│  └──────────────────────┘       └──────────────────────────────┘       │
│          │                                    │                          │
│          ▼                                    ▼                          ▼
│  /api/auth/login                   ReservationTimeoutTask             seat_reservation
│  /api/reservation          ┌──────────────────────────────────┐       ├── sys_user
│  /api/seat                 │ Result 统一返回                  │       ├── seat
│  /api/check                │ DTO 数据传输                     │       ├── reservation
│  /api/log                  │ GlobalException 全局异常         │       ├── check_record
│                             └──────────────────────────────────┘       └── operation_log
│                                                                             │
│  Docker 容器编排                                                             │
│  docker-compose.yml ───▶  frontend-admin + backend + mysql                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 数据流图(预约场景)

```
用户点击预约
    │
    ▼
[前端Vue组件]
    │ SeatDTO(seatId, reserveDate, timeSlot)
    ▼
HTTP POST /api/reservation
    │
    ▼
[JwtInterceptor]
    │ 1. 校验Token有效
    │ 2. 解析出userId
    ▼
[ReservationController.create]
    │ 调用 service.create(userId, dto)
    ▼
[ReservationServiceImpl.createReservation]
    │
    ├─────────────────────────────────┐
    │ 1. 日期、时段合法性校验          │
    │ 2. 查询seat：status != 2        │
    │ 3. selectCount 该时段seat已预约? │
    │ 4. selectCount user是否重复预约 │
    │ 5. reservation insert(status=0)│
    │ 6. seat update(status=1)        │
    └─────────────────────────────────┘
              │ 两个表更新在一个事务
              ▼ @Transactional
[MySQL - seat & reservation 表原子更新]
    │
    ├──── reservation表：一条status=0的预约
    └──── seat表：对应id座位status=1

                              ┌─────────────────────────┐
                              │ ReservationTimeoutTask  │
                              │ 每分钟扫描 status=0      │
                              │ 超时则 set status=4     │
                              │ 并恢复 seat status=0    │
                              └─────────────────────────┘
```

## 五、核心类表结构

### 5.1 reservation表 (核心状态承载)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | PK |
| user_id | BIGINT | FK 用户 |
| seat_id | BIGINT | FK 座位 |
| reserve_date | DATE | 预约日期 |
| time_slot | VARCHAR | 时段如"08:00-10:00" |
| **status** | TINYINT | **核心状态字段** |
| check_in_time | DATETIME | 签到时间 |

### 5.2 seat表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | PK |
| seat_no | VARCHAR | A-001 格式 |
| **status** | TINYINT | 0/1/2 |
| has_charger/near_window | TINYINT | 属性标签 |
| area, floor | 属性 | 区域、楼层 |
