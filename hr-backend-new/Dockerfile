# 构建阶段：Maven + JDK17
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先拷贝父 POM 与模块 POM，利用 Docker 层缓存
COPY pom.xml .
COPY hr-common/pom.xml hr-common/
COPY hr-auth/pom.xml hr-auth/
COPY hr-demand/pom.xml hr-demand/
COPY hr-talent/pom.xml hr-talent/
COPY hr-interview/pom.xml hr-interview/
COPY hr-hire/pom.xml hr-hire/
COPY hr-dashboard/pom.xml hr-dashboard/
COPY hr-config/pom.xml hr-config/
COPY hr-integration/pom.xml hr-integration/
COPY hr-bootstrap/pom.xml hr-bootstrap/
RUN mvn dependency:go-offline -B -q || true

# 拷贝源码并打包
COPY . .
RUN mvn package -DskipTests -B -q

# 运行阶段
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/hr-bootstrap/target/hr-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xmx512m", "-jar", "/app/app.jar"]
