# ===== 构建阶段 =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先复制 pom.xml 利用缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 再复制源码
COPY src ./src
RUN mvn package -DskipTests -B

# ===== 运行阶段 =====
FROM eclipse-temurin:17-jre-alpine

# 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
USER appuser

COPY --from=build /app/target/task-app-1.0.0.jar app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
