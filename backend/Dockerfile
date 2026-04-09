# ========== 构建阶段 ==========
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 先复制 pom.xml 利用 Docker 缓存下载依赖
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并构建
COPY src ./src
RUN mvn clean package -DskipTests

# ========== 运行阶段 ==========
FROM eclipse-temurin:17-jre

WORKDIR /app

# 安装字体和图形库，确保 ImageIO 在 headless 环境下正常工作
# 设置时区为亚洲/上海
RUN apt-get update && apt-get install -y --no-install-recommends \
    fontconfig libfreetype6 libfontconfig1 tzdata \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

# 从构建阶段复制 jar
COPY --from=builder /app/target/*.jar app.jar

# 创建上传目录
RUN mkdir -p /app/uploads

EXPOSE 8088

ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]
