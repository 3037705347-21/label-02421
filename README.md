# 图书馆自习座位预约系统

## How to Run

确保已安装 Docker 和 Docker Compose，然后在项目根目录执行：

```bash
docker-compose up --build -d
```

等待所有服务启动完成后：
- 管理后台前端：http://localhost:8081
- 后端 API：http://localhost:8088

停止服务：
```bash
docker-compose down
```

清除数据重新初始化：
```bash
docker-compose down -v
docker-compose up --build -d
```

## Services

| 服务 | 说明 | 容器名 | 端口映射 |
|------|------|--------|----------|
| mysql | MySQL 8.0 数据库 | seat-mysql | 3307:3306 |
| backend | Spring Boot 后端 API | seat-backend | 8088:8088 |
| frontend-admin | Vue 3 管理后台 | seat-frontend-admin | 8081:80 |

## 测试账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | 123456 |

普通用户请通过注册页面自行注册。

## 题目内容

根据文件要求，开发一个Java web网站是一个图书管自习座位预约网站。实现功能座位预约，是否有充电孔和是否靠窗，
到场签到，自习结束座位垃圾清理拍照打卡。告诉我网站代码和实现网站的具体步骤，下载哪些软件和配置哪些环境。

---

## 项目介绍

一个基于 Java Web 的图书馆自习座位预约网站，支持座位预约（充电孔/靠窗筛选）、到场签到、自习结束清洁拍照打卡（含自动清洁度检测）等功能。

## 技术栈

- **后端**: Java 17 + Spring Boot 3.2 + MyBatis-Plus + MySQL 8.0
- **前端**: Vue 3 + Vite 5 + Element Plus + Pinia + Axios
- **认证**: JWT Token
- **日志**: AOP 操作日志
- **部署**: 支持本地运行 / Docker Compose 一键部署

## 功能模块

### 用户端
- 座位列表浏览（支持按区域/充电孔/靠窗筛选）
- 在线预约座位（选择日期和时间段，自动过滤已过期时段）
- 到场签到
- 自习结束拍照打卡（图像清洁度自动检测 + 管理员人工复审）
- 我的预约记录查看
- 超时未签到自动取消（定时任务，开始时间后30分钟未签到自动标记超时并释放座位）

### 管理端
- 座位增删改查管理
- 全部预约记录查看
- 用户管理（启用/禁用）
- 打卡记录审核（含清洁度评分参考 + 人工通过/不通过）
- 操作日志查看

---

## 本地开发环境部署

### 第一步：下载安装所需软件

| 软件 | 版本要求 | 用途 | 下载地址 |
|------|----------|------|----------|
| JDK | 17 或以上 | 运行后端 Java 项目 | https://adoptium.net/ |
| Maven | 3.8 或以上 | 构建后端项目、管理依赖 | https://maven.apache.org/download.cgi |
| MySQL | 8.0 或以上 | 数据库存储 | https://dev.mysql.com/downloads/mysql/ |
| Node.js | 18 或以上 | 运行前端项目 | https://nodejs.org/zh-cn |
| npm | 9 或以上 | 前端包管理（随 Node.js 自动安装） | - |

### 第二步：配置环境变量

#### JDK 配置
1. 安装 JDK 17 后，设置系统环境变量：
   - `JAVA_HOME` = JDK 安装目录（如 `C:\Program Files\Java\jdk-17`）
   - 将 `%JAVA_HOME%\bin` 添加到系统 `PATH`
2. 验证：打开终端执行 `java -version`，应显示 `openjdk version "17.x.x"`

#### Maven 配置
1. 下载解压 Maven，设置环境变量：
   - `MAVEN_HOME` = Maven 解压目录
   - 将 `%MAVEN_HOME%\bin` 添加到系统 `PATH`
2. （推荐）配置阿里云镜像加速，编辑 `MAVEN_HOME/conf/settings.xml`，在 `<mirrors>` 中添加：
   ```xml
   <mirror>
     <id>aliyunmaven</id>
     <mirrorOf>*</mirrorOf>
     <name>阿里云公共仓库</name>
     <url>https://maven.aliyun.com/repository/public</url>
   </mirror>
   ```
3. 验证：执行 `mvn -version`

#### Node.js 配置
1. 安装 Node.js 后验证：`node -v` 和 `npm -v`
2. （推荐）设置 npm 淘宝镜像加速：
   ```bash
   npm config set registry https://registry.npmmirror.com
   ```

#### MySQL 配置
1. 安装 MySQL 8.0，安装过程中设置 root 密码（如 `123456`）
2. 确保 MySQL 服务已启动，默认端口 `3306`
3. 验证：执行 `mysql -u root -p` 能正常登录

### 第三步：初始化数据库

使用命令行或数据库管理工具（如 Navicat、DataGrip、DBeaver）执行建表脚本：

```bash
# 命令行方式
mysql -u root -p < backend/sql/schema.sql
```

或者打开数据库工具，连接 MySQL 后，打开 `backend/sql/schema.sql` 文件执行全部 SQL。

该脚本会自动完成：
- 创建 `seat_reservation` 数据库
- 创建所有数据表（用户表、座位表、预约表、打卡记录表、操作日志表）
- 插入管理员账号和示例座位数据

### 第四步：配置数据库连接

编辑后端配置文件 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/seat_reservation?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root          # ← 改为你的 MySQL 用户名
    password: 123456        # ← 改为你的 MySQL 密码
```

如果你的 MySQL 端口不是默认的 3306，也需要修改 url 中的端口号。

### 第五步：启动后端

```bash
cd backend

# 安装依赖并编译
mvn clean install -DskipTests

# 启动 Spring Boot 应用
mvn spring-boot:run
```

启动成功后终端会显示：`Started SeatReservationApplication`

后端运行在：http://localhost:8088

### 第六步：启动前端

```bash
cd frontend-admin

# 安装前端依赖
npm install

# 启动开发服务器
npm run dev
```

启动成功后终端会显示本地访问地址。

前端运行在：http://localhost:5173

### 第七步：访问系统

打开浏览器访问 http://localhost:5173 即可使用系统。

---

## 项目结构

```
├── backend/                                        # 后端项目 (Spring Boot)
│   ├── Dockerfile
│   ├── pom.xml
│   ├── sql/
│   │   └── schema.sql                              # 数据库初始化脚本
│   └── src/main/
│       ├── java/com/library/seat/
│       │   ├── SeatReservationApplication.java      # 启动类
│       │   ├── aop/
│       │   │   ├── LogOperation.java                # 日志注解
│       │   │   └── OperationLogAspect.java          # 日志切面
│       │   ├── config/
│       │   │   ├── CorsConfig.java                  # 跨域配置
│       │   │   ├── JwtInterceptor.java              # JWT 拦截器
│       │   │   ├── MybatisPlusConfig.java           # MyBatis-Plus 配置
│       │   │   └── WebMvcConfig.java                # MVC 配置（拦截器/静态资源）
│       │   ├── controller/
│       │   │   ├── AuthController.java              # 认证（登录/注册/用户信息）
│       │   │   ├── CheckRecordController.java       # 打卡记录（上传/查看/审核）
│       │   │   ├── OperationLogController.java      # 操作日志
│       │   │   ├── ReservationController.java       # 预约（创建/签到/取消）
│       │   │   ├── SeatController.java              # 座位管理
│       │   │   └── UserController.java              # 用户管理
│       │   ├── dto/
│       │   │   ├── LoginDTO.java
│       │   │   ├── RegisterDTO.java
│       │   │   ├── ReservationDTO.java
│       │   │   └── SeatDTO.java
│       │   ├── entity/
│       │   │   ├── CheckRecord.java
│       │   │   ├── OperationLog.java
│       │   │   ├── Reservation.java
│       │   │   ├── Seat.java
│       │   │   └── User.java
│       │   ├── exception/
│       │   │   ├── BusinessException.java           # 业务异常
│       │   │   └── GlobalExceptionHandler.java      # 全局异常处理
│       │   ├── mapper/
│       │   │   ├── CheckRecordMapper.java
│       │   │   ├── OperationLogMapper.java
│       │   │   ├── ReservationMapper.java
│       │   │   ├── SeatMapper.java
│       │   │   └── UserMapper.java
│       │   ├── service/
│       │   │   ├── AuthService.java
│       │   │   ├── CheckRecordService.java
│       │   │   ├── CleanDetectionService.java       # 图像清洁度自动检测
│       │   │   ├── ReservationService.java
│       │   │   ├── SeatService.java
│       │   │   ├── UserService.java
│       │   │   └── impl/
│       │   │       ├── AuthServiceImpl.java
│       │   │       ├── CheckRecordServiceImpl.java
│       │   │       ├── ReservationServiceImpl.java
│       │   │       ├── SeatServiceImpl.java
│       │   │       └── UserServiceImpl.java
│       │   ├── task/
│       │   │   └── ReservationTimeoutTask.java      # 超时未到定时任务
│       │   └── util/
│       │       ├── JwtUtil.java                     # JWT 工具类
│       │       └── Result.java                      # 统一响应封装
│       └── resources/
│           └── application.yml                      # 应用配置
├── frontend-admin/                                  # 前端项目 (Vue 3)
│   ├── Dockerfile
│   ├── index.html
│   ├── nginx.conf
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── App.vue
│       ├── main.js
│       ├── api/
│       │   ├── index.js                             # API 接口定义
│       │   └── request.js                           # Axios 拦截器封装
│       ├── assets/styles/
│       │   └── global.scss                          # 全局样式
│       ├── router/
│       │   └── index.js                             # 路由配置（含登录拦截）
│       ├── store/
│       │   └── user.js                              # Pinia 用户状态管理
│       └── views/
│           ├── Layout.vue                           # 主布局（侧边栏+顶栏）
│           ├── Login.vue                            # 登录/注册页
│           ├── admin/
│           │   ├── CheckRecordManage.vue            # 打卡记录审核
│           │   ├── LogManage.vue                    # 操作日志
│           │   ├── ReservationManage.vue            # 预约管理
│           │   ├── SeatManage.vue                   # 座位管理
│           │   └── UserManage.vue                   # 用户管理
│           └── user/
│               ├── MyReservations.vue               # 我的预约
│               └── SeatList.vue                     # 座位列表
├── docs/
│   └── project_design.md                            # 项目设计文档
├── docker-compose.yml
├── .gitignore
└── README.md
```
