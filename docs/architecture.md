# 图书馆自习座位预约系统 - 架构设计与状态流转文档

## 目录

1. [系统概述](#系统概述)
2. [座位状态流转详解](#座位状态流转详解)
3. [预约状态流转详解](#预约状态流转详解)
4. [代码实现分析](#代码实现分析)
5. [设计模式与架构思想](#设计模式与架构思想)
6. [系统架构图](#系统架构图)

---

## 系统概述

图书馆自习座位预约系统是一个基于 Spring Boot + Vue3 的全栈应用，实现了座位的在线预约、签到、打卡离座等完整业务流程。系统支持两种角色：普通用户和管理员，分别拥有不同的功能权限。

### 核心功能模块

- **用户认证模块**：登录、注册、JWT Token 鉴权
- **座位管理模块**：座位的增删改查、状态管理
- **预约管理模块**：预约创建、取消、签到
- **打卡离座模块**：上传照片、自动清洁度检测、人工审核
- **日志管理模块**：操作日志记录与查询

---

## 座位状态流转详解

### 座位状态定义

座位（Seat）实体定义了三种状态：

| 状态值 | 状态名称 | 说明 |
|--------|----------|------|
| 0 | 可用 | 座位空闲，可以被预约 |
| 1 | 已预约 | 座位已被预约，当前不可再被预约 |
| 2 | 维护中 | 座位处于维护状态，暂不可预约 |

### 座位状态流转图

```
┌─────────┐     创建座位      ┌─────────┐
│  初始   │ ───────────────→ │  可用   │
│ (null)  │                  │  (0)    │
└─────────┘                  └────┬────┘
                                  │
                    ┌─────────────┼─────────────┐
                    │             │             │
                    ▼             │             ▼
              ┌─────────┐         │        ┌─────────┐
              │ 已预约  │         │        │ 维护中  │
              │  (1)    │ ←───────┘        │  (2)    │
              └────┬────┘   取消预约/       └────┬────┘
                   │       打卡离座/             │
                   │       超时释放              │
                   │                            │
                   └──────────────┬─────────────┘
                                  │
                                  ▼
                            ┌─────────┐
                            │  可用   │
                            │  (0)    │
                            └─────────┘
```

### 座位状态流转规则

1. **可用 → 已预约**：用户成功创建预约时触发
   - 代码位置：[ReservationServiceImpl.java:89](../backend/src/main/java/com/library/seat/service/impl/ReservationServiceImpl.java#L89)
   - 触发条件：预约创建成功且通过所有校验

2. **已预约 → 可用**：以下三种情况会触发
   - **用户取消预约**：[ReservationServiceImpl.java:140-143](../backend/src/main/java/com/library/seat/service/impl/ReservationServiceImpl.java#L140-L143)
   - **用户打卡离座**：[CheckRecordServiceImpl.java:146-149](../backend/src/main/java/com/library/seat/service/impl/CheckRecordServiceImpl.java#L146-L149)
   - **预约超时自动释放**：[ReservationTimeoutTask.java:80-83](../backend/src/main/java/com/library/seat/task/ReservationTimeoutTask.java#L80-L83)

3. **可用 ↔ 维护中**：管理员手动切换
   - 通过管理员界面的座位管理功能修改

---

## 预约状态流转详解

### 预约状态定义

预约（Reservation）实体定义了五种状态：

| 状态值 | 状态名称 | 说明 |
|--------|----------|------|
| 0 | 待使用 | 预约已创建，等待用户签到 |
| 1 | 使用中 | 用户已签到，正在使用座位 |
| 2 | 已完成 | 用户已打卡离座，流程结束 |
| 3 | 已取消 | 用户主动取消预约 |
| 4 | 超时未到 | 用户未在指定时间内签到，系统自动标记 |

### 预约状态流转图

```
                              ┌─────────────────────────────────────┐
                              │                                     │
                              │  定时任务扫描：                      │
                              │  1. 预约日期已过                    │
                              │  2. 时段开始30分钟后仍未签到        │
                              │                                     │
                              ▼                                     │
┌─────────┐    创建预约    ┌─────────┐   签到      ┌─────────┐      │
│  初始   │ ────────────→ │ 待使用  │ ─────────→ │ 使用中  │      │
│ (null)  │              │  (0)    │            │  (1)    │      │
└─────────┘              └────┬────┘            └────┬────┘      │
                              │                        │           │
              ┌───────────────┘                        │           │
              │                                        │           │
              │ 取消预约                               │ 打卡离座  │
              ▼                                        ▼           │
        ┌─────────┐                              ┌─────────┐       │
        │ 已取消  │                              │ 已完成  │       │
        │  (3)    │                              │  (2)    │       │
        └─────────┘                              └─────────┘       │
                                                                   │
                              ┌─────────┐ ◄────────────────────────┘
                              │ 超时未到│
                              │  (4)    │
                              └────┬────┘
                                   │
                                   │ 释放座位
                                   ▼
                            ┌─────────┐
                            │ 座位恢复│
                            │ 可用状态│
                            └─────────┘
```

### 预约状态流转规则

1. **创建预约（初始 → 待使用）**
   - 代码位置：[ReservationServiceImpl.java:36-91](../backend/src/main/java/com/library/seat/service/impl/ReservationServiceImpl.java#L36-L91)
   - 校验规则：
     - 预约日期不能早于今天
     - 当天已过时间段不可预约
     - 座位必须存在且不在维护中
     - 同一时间段座位不能被重复预约
     - 用户同一时间段只能有一个预约

2. **取消预约（待使用 → 已取消）**
   - 代码位置：[ReservationServiceImpl.java:125-146](../backend/src/main/java/com/library/seat/service/impl/ReservationServiceImpl.java#L125-L146)
   - 权限校验：只能取消自己的预约
   - 状态校验：只有"待使用"状态的预约可以取消

3. **签到（待使用 → 使用中）**
   - 代码位置：[ReservationServiceImpl.java:149-195](../backend/src/main/java/com/library/seat/service/impl/ReservationServiceImpl.java#L149-L195)
   - 时间校验规则：
     - 只允许在预约日期当天签到
     - 最早可提前15分钟签到
     - 时段结束后不可签到

4. **打卡离座（使用中 → 已完成）**
   - 代码位置：[CheckRecordServiceImpl.java:57-160](../backend/src/main/java/com/library/seat/service/impl/CheckRecordServiceImpl.java#L57-L160)
   - 包含流程：上传照片 → 自动清洁度检测 → 保存记录 → 更新预约状态 → 释放座位

5. **超时处理（待使用 → 超时未到）**
   - 代码位置：[ReservationTimeoutTask.java:35-95](../backend/src/main/java/com/library/seat/task/ReservationTimeoutTask.java#L35-L95)
   - 触发条件：
     - 预约日期已过（昨天及以前的预约）
     - 当天预约时段开始30分钟后仍未签到
   - 执行频率：每分钟扫描一次

---

## 代码实现分析

### 1. 状态流转的业务逻辑实现

#### 预约创建流程

```java
@Transactional
public void createReservation(Long userId, ReservationDTO dto) {
    // 1. 日期校验 - 不能预约过去的日期
    if (dto.getReserveDate().isBefore(LocalDate.now())) {
        throw new BusinessException("预约日期不能早于今天");
    }

    // 2. 时间段校验 - 当天已过时段不可预约
    if (dto.getReserveDate().isEqual(LocalDate.now())) {
        String timeSlot = dto.getTimeSlot();
        String endTimeStr = timeSlot.split("-")[1].trim();
        LocalTime slotEnd = LocalTime.parse(endTimeStr);
        if (LocalTime.now().isAfter(slotEnd)) {
            throw new BusinessException("该时间段已结束");
        }
    }

    // 3. 座位状态校验
    Seat seat = seatMapper.selectById(dto.getSeatId());
    if (seat.getStatus() == 2) {
        throw new BusinessException("该座位维护中，暂不可预约");
    }

    // 4. 并发冲突校验 - 同一时间段不能重复预约
    Long count = reservationMapper.selectCount(
        new LambdaQueryWrapper<Reservation>()
            .eq(Reservation::getSeatId, dto.getSeatId())
            .eq(Reservation::getReserveDate, dto.getReserveDate())
            .eq(Reservation::getTimeSlot, dto.getTimeSlot())
            .in(Reservation::getStatus, 0, 1));
    if (count > 0) {
        throw new BusinessException("该座位在此时间段已被预约");
    }

    // 5. 创建预约记录
    Reservation reservation = new Reservation();
    reservation.setUserId(userId);
    reservation.setSeatId(dto.getSeatId());
    reservation.setReserveDate(dto.getReserveDate());
    reservation.setTimeSlot(dto.getTimeSlot());
    reservation.setStatus(0); // 待使用状态
    reservationMapper.insert(reservation);

    // 6. 更新座位状态为已预约
    seat.setStatus(1);
    seatMapper.updateById(seat);
}
```

#### 签到流程

```java
@Transactional
public void checkIn(Long userId, Long id) {
    Reservation reservation = reservationMapper.selectById(id);

    // 1. 权限校验
    if (!reservation.getUserId().equals(userId)) {
        throw new BusinessException("无权操作此预约");
    }

    // 2. 状态校验
    if (reservation.getStatus() != 0) {
        throw new BusinessException("当前状态不可签到");
    }

    // 3. 日期校验
    LocalDate today = LocalDate.now();
    LocalDate reserveDate = reservation.getReserveDate();
    if (reserveDate.isAfter(today)) {
        throw new BusinessException("预约日期还未到，无法签到");
    }
    if (reserveDate.isBefore(today)) {
        throw new BusinessException("预约日期已过，无法签到");
    }

    // 4. 时间段校验 - 提前15分钟到结束时间之间可签到
    String timeSlot = reservation.getTimeSlot();
    String[] parts = timeSlot.split("-");
    LocalTime slotStart = LocalTime.parse(parts[0].trim());
    LocalTime slotEnd = LocalTime.parse(parts[1].trim());
    LocalTime now = LocalTime.now();

    LocalTime earliestCheckIn = slotStart.minusMinutes(15);
    if (now.isBefore(earliestCheckIn)) {
        throw new BusinessException("签到时间未到");
    }
    if (now.isAfter(slotEnd)) {
        throw new BusinessException("预约时间段已结束");
    }

    // 5. 更新状态为使用中
    reservation.setStatus(1);
    reservation.setCheckInTime(LocalDateTime.now());
    reservationMapper.updateById(reservation);
}
```

#### 定时超时处理

```java
@Scheduled(fixedRate = 60000) // 每分钟执行一次
@Transactional
public void checkTimeoutReservations() {
    LocalDate today = LocalDate.now();
    LocalTime now = LocalTime.now();

    // 查询所有待使用状态的预约
    List<Reservation> pendingList = reservationMapper.selectList(
        new LambdaQueryWrapper<Reservation>()
            .eq(Reservation::getStatus, 0)
            .eq(Reservation::getDeleted, 0)
    );

    for (Reservation reservation : pendingList) {
        boolean shouldTimeout = false;

        // 规则1：预约日期已过，直接超时
        if (reservation.getReserveDate().isBefore(today)) {
            shouldTimeout = true;
        }
        // 规则2：当天预约，时段开始30分钟后未签到则超时
        else if (reservation.getReserveDate().isEqual(today)) {
            String timeSlot = reservation.getTimeSlot();
            String startTimeStr = timeSlot.split("-")[0].trim();
            LocalTime slotStart = LocalTime.parse(startTimeStr);
            if (now.isAfter(slotStart.plusMinutes(30))) {
                shouldTimeout = true;
            }
        }

        if (shouldTimeout) {
            reservation.setStatus(4); // 标记为超时未到
            reservationMapper.updateById(reservation);

            // 释放座位
            Seat seat = seatMapper.selectById(reservation.getSeatId());
            if (seat != null && seat.getStatus() == 1) {
                seat.setStatus(0);
                seatMapper.updateById(seat);
            }
        }
    }
}
```

---

## 设计模式与架构思想

### 1. 分层架构模式（Layered Architecture）

系统采用经典的三层架构：

```
┌─────────────────────────────────────────┐
│           Controller 层                  │  ← 处理HTTP请求，参数校验
│  (ReservationController, SeatController) │
├─────────────────────────────────────────┤
│           Service 层                     │  ← 业务逻辑处理，事务管理
│  (ReservationServiceImpl, etc.)          │
├─────────────────────────────────────────┤
│           Mapper/DAO 层                  │  ← 数据访问，SQL执行
│  (ReservationMapper, etc.)               │
├─────────────────────────────────────────┤
│           Database                       │  ← MySQL 数据存储
└─────────────────────────────────────────┘
```

**优点**：
- 职责分离，各层专注自己的职责
- 便于单元测试和代码维护
- 降低层与层之间的耦合度

### 2. 拦截器模式（Interceptor Pattern）

#### JWT 权限校验拦截器

```java
@Component
public class JwtInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            response.setStatus(401);
            return false;
        }

        token = token.substring(7);
        if (jwtUtil.isTokenExpired(token)) {
            response.setStatus(401);
            return false;
        }

        // 将用户信息存入request属性，供后续使用
        request.setAttribute("userId", jwtUtil.getUserId(token));
        request.setAttribute("role", jwtUtil.getRole(token));
        return true;
    }
}
```

**配置方式**：
```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/auth/login", "/api/auth/register");
    }
}
```

**设计思想**：
- **横切关注点分离**：将认证逻辑从业务代码中抽离
- **职责链模式**：多个拦截器可以形成处理链
- **开闭原则**：新增拦截器无需修改现有代码

### 3. 面向切面编程（AOP - Aspect Oriented Programming）

#### 操作日志记录切面

```java
@Aspect
@Component
public class OperationLogAspect {

    // 定义切点：所有带有 @LogOperation 注解的方法
    @Pointcut("@annotation(com.library.seat.aop.LogOperation)")
    public void logPointcut() {}

    // 环绕通知：在目标方法执行前后进行处理
    @Around("logPointcut() && @annotation(logOperation)")
    public Object around(ProceedingJoinPoint point,
                        LogOperation logOperation) throws Throwable {
        // 先执行目标方法
        Object result = point.proceed();

        // 方法执行成功后记录日志
        try {
            HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes()).getRequest();

            OperationLog opLog = new OperationLog();
            opLog.setUserId((Long) request.getAttribute("userId"));
            opLog.setModule(logOperation.module());
            opLog.setAction(logOperation.action());
            opLog.setDetail(logOperation.detail());
            opLog.setIp(getIpAddress(request));
            opLog.setCreateTime(LocalDateTime.now());
            operationLogMapper.insert(opLog);
        } catch (Exception e) {
            log.error("记录操作日志失败: ", e);
        }
        return result;
    }
}
```

**使用方式**：
```java
@PostMapping
@LogOperation(module = "预约管理", action = "创建", detail = "创建座位预约")
public Result<?> create(HttpServletRequest request,
                       @Valid @RequestBody ReservationDTO dto) {
    Long userId = (Long) request.getAttribute("userId");
    reservationService.createReservation(userId, dto);
    return Result.success();
}
```

**设计思想**：
- **关注点分离**：日志记录与业务逻辑解耦
- **声明式编程**：通过注解声明需要记录日志的操作
- **非侵入性**：业务代码无需关心日志实现

### 4. 策略模式（Strategy Pattern）

#### 清洁度检测的多策略实现

```java
@Service
public class CleanDetectionService {

    // 主检测策略：基于图像像素分析
    public Map<String, Object> analyzeCleanness(File imageFile) {
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                // 策略切换：主策略失败时使用备用策略
                return fallbackAnalysis(imageFile);
            }
            // 执行图像分析...
            return performImageAnalysis(image);
        } catch (Exception e) {
            // 异常时切换到备用策略
            return fallbackAnalysis(imageFile);
        }
    }

    // 备用检测策略：基于文件字节分析
    private Map<String, Object> fallbackAnalysis(File imageFile) {
        // 当 ImageIO 无法解析图片时使用
        // 基于字节值分布进行简单分析
    }
}
```

**设计思想**：
- **策略封装**：不同检测算法封装为不同策略
- **运行时切换**：根据环境自动选择可用策略
- **容错设计**：主策略失败时自动降级到备用策略

### 5. 统一异常处理（Global Exception Handler）

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 处理业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    // 处理参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("参数校验失败");
        return Result.error(400, message);
    }

    // 处理其他所有异常
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.error("系统内部错误");
    }
}
```

**设计思想**：
- **集中式异常处理**：所有异常统一处理，避免重复代码
- **统一响应格式**：确保API返回一致的格式
- **异常分类处理**：不同类型的异常采用不同的处理策略

### 6. 依赖注入（Dependency Injection）

系统大量使用 Spring 的依赖注入：

```java
@Service
@RequiredArgsConstructor // Lombok 生成包含 final 字段的构造器
public class ReservationServiceImpl implements ReservationService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final UserMapper userMapper;

    // 构造器注入由 Spring 自动完成
    // 无需 @Autowired 注解
}
```

**设计思想**：
- **控制反转（IoC）**：对象创建和管理交给容器
- **依赖倒置**：依赖于抽象（接口）而非具体实现
- **可测试性**：便于Mock依赖进行单元测试

### 7. 事务管理（Transaction Management）

```java
@Transactional
public void createReservation(Long userId, ReservationDTO dto) {
    // 1. 插入预约记录
    reservationMapper.insert(reservation);

    // 2. 更新座位状态
    seat.setStatus(1);
    seatMapper.updateById(seat);

    // 两个操作要么都成功，要么都失败
}
```

**设计思想**：
- **声明式事务**：通过注解声明事务边界
- **ACID保证**：确保数据一致性
- **自动回滚**：异常时自动回滚事务

---

## 系统架构图

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              前端层 (Frontend)                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Vue 3 + Vite                                 │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐ │   │
│  │  │  用户端界面  │  │  管理端界面  │  │  Vue Router │  │  Pinia Store│ │   │
│  │  │  (座位预约)  │  │ (座位/用户/  │  │  (动态路由)  │  │  (状态管理) │ │   │
│  │  │             │  │  预约管理)   │  │             │  │            │ │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘ │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│                                    │ HTTP/RESTful API                       │
│                                    ▼                                        │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                              网关层 (Gateway)                                │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      Nginx 反向代理                                  │   │
│  │         (静态资源服务 / 负载均衡 / 跨域处理)                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                           后端层 (Backend)                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     Spring Boot 应用                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │                    Controller 层                             │   │   │
│  │  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────┐ │   │   │
│  │  │  │AuthController│ │SeatController│ │Reservation │ │Check   │ │   │   │
│  │  │  │             │ │             │ │Controller   │ │Controller│   │   │
│  │  │  └─────────────┘ └─────────────┘ └─────────────┘ └────────┘ │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  │                              │                                      │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │                    Service 层                                │   │   │
│  │  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────┐ │   │   │
│  │  │  │AuthService  │ │SeatService  │ │Reservation │ │Check   │ │   │   │
│  │  │  │             │ │             │ │Service      │ │Service │ │   │   │
│  │  │  └─────────────┘ └─────────────┘ └─────────────┘ └────────┘ │   │   │
│  │  │  ┌─────────────────────────────────────────────────────────┐ │   │   │
│  │  │  │         CleanDetectionService (图像检测服务)              │ │   │   │
│  │  │  └─────────────────────────────────────────────────────────┘ │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  │                              │                                      │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │                    Mapper/DAO 层                             │   │   │
│  │  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────┐ │   │   │
│  │  │  │UserMapper   │ │SeatMapper   │ │Reservation │ │Check   │ │   │   │
│  │  │  │             │ │             │ │Mapper       │ │Mapper  │ │   │   │
│  │  │  └─────────────┘ └─────────────┘ └─────────────┘ └────────┘ │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  │                                                                     │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │                    横切关注点 (Cross-cutting)                 │   │   │
│  │  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────┐ │   │   │
│  │  │  │JwtInterceptor│ │OperationLog │ │GlobalException│ │Transaction│   │   │
│  │  │  │  (认证拦截)  │ │Aspect (日志) │ │Handler (异常) │ │(事务管理) │   │   │
│  │  │  └─────────────┘ └─────────────┘ └─────────────┘ └────────┘ │   │   │
│  │  │  ┌─────────────────────────────────────────────────────────┐ │   │   │
│  │  │  │         ReservationTimeoutTask (定时任务)                 │ │   │   │
│  │  │  └─────────────────────────────────────────────────────────┘ │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                           数据层 (Data Layer)                                │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         MySQL 数据库                                 │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────────┐    │   │
│  │  │   sys_user  │ │    seat     │ │ reservation │ │check_record│    │   │
│  │  │   (用户表)   │ │   (座位表)   │ │   (预约表)   │ │  (打卡表)   │    │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └────────────┘    │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │                    operation_log (操作日志表)                 │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 数据库 ER 图

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│    sys_user     │         │      seat       │         │  reservation    │
├─────────────────┤         ├─────────────────┤         ├─────────────────┤
│ PK id           │         │ PK id           │         │ PK id           │
│    username     │         │    seat_no      │◄────────┤ FK user_id      │
│    password     │         │    has_charger  │         │ FK seat_id      │
│    nickname     │         │    near_window  │         │    reserve_date │
│    phone        │         │    status       │         │    time_slot    │
│    role         │         │    area         │         │    status       │
│    create_time  │         │    floor        │         │    check_in_time│
│    update_time  │         │    create_time  │         │    create_time  │
│    deleted      │         │    update_time  │         │    update_time  │
└─────────────────┘         │    deleted      │         │    deleted      │
                            └─────────────────┘         └────────┬────────┘
                                                                 │
                                                                 │
                                                                 ▼
                                                        ┌─────────────────┐
                                                        │  check_record   │
                                                        ├─────────────────┤
                                                        │ PK id           │
                                                        │ FK reservation_id│
                                                        │ FK user_id      │
                                                        │    photo_url    │
                                                        │    clean_passed │
                                                        │    auto_clean_  │
                                                        │      score      │
                                                        │    auto_clean_  │
                                                        │      result     │
                                                        │    auto_clean_  │
                                                        │      detail     │
                                                        │    remark       │
                                                        │    create_time  │
                                                        └─────────────────┘

┌─────────────────┐
│  operation_log  │
├─────────────────┤
│ PK id           │
│    user_id      │
│    module       │
│    action       │
│    detail       │
│    ip           │
│    create_time  │
└─────────────────┘
```

### 请求处理流程图

```
┌─────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Client │────►│    Nginx    │────►│   Spring    │────►│   Jwt       │
│         │     │             │     │   Boot      │     │ Interceptor │
└─────────┘     └─────────────┘     └─────────────┘     └──────┬──────┘
                                                               │
                                                               │ Token校验
                                                               │
                                                               ▼
┌─────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Client │◄────│   Nginx     │◄────│   Spring    │◄────│  Controller │
│         │     │             │     │   Boot      │     │             │
└─────────┘     └─────────────┘     └─────────────┘     └──────┬──────┘
                                                               │
                                                               │ 调用
                                                               ▼
                                                        ┌─────────────┐
                                                        │   Service   │
                                                        │             │
                                                        └──────┬──────┘
                                                               │
                                                               │ 调用
                                                               ▼
                                                        ┌─────────────┐
                                                        │   Mapper    │
                                                        │  (MyBatis)  │
                                                        └──────┬──────┘
                                                               │
                                                               │ SQL
                                                               ▼
                                                        ┌─────────────┐
                                                        │    MySQL    │
                                                        │             │
                                                        └─────────────┘
```

### 前端路由权限控制流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                        路由守卫 (router.beforeEach)                  │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
                    ┌───────────────────────────────┐
                    │     是否访问登录页?            │
                    └───────────────────────────────┘
                          │               │
                         是              否
                          │               │
                          ▼               ▼
            ┌─────────────────┐   ┌─────────────────┐
            │  已登录?        │   │  有Token?       │
            │  ──→ 跳转首页   │   │  否 ──→ 跳转登录 │
            │  否 ──→ 允许访问 │   │  是 ──→ 继续    │
            └─────────────────┘   └─────────────────┘
                                          │
                                          ▼
                              ┌─────────────────────┐
                              │   路由已添加?        │
                              └─────────────────────┘
                                    │           │
                                   否          是
                                    │           │
                                    ▼           ▼
                        ┌─────────────────┐   ┌─────────────────┐
                        │  获取用户信息    │   │  正常导航        │
                        │  根据角色添加路由│   │                 │
                        │  重定向到目标页  │   │                 │
                        └─────────────────┘   └─────────────────┘
```

---

## 总结

本系统采用了多种成熟的设计模式和架构思想：

1. **分层架构**：清晰的三层结构保证了代码的可维护性
2. **拦截器 + AOP**：实现了横切关注点的优雅处理
3. **策略模式**：清洁度检测的多策略实现保证了系统鲁棒性
4. **统一异常处理**：保证了API响应的一致性
5. **依赖注入**：降低了组件间的耦合度
6. **声明式事务**：简化了事务管理代码

状态流转方面，系统通过数据库事务保证了座位状态和预约状态的一致性，通过定时任务处理超时预约，确保资源的及时释放。权限校验贯穿整个请求处理流程，从网关层到控制器层都有相应的校验机制。
