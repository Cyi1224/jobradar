# ── 构建阶段：用 Maven 打包 ──
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ── 运行阶段：精简 JRE ──
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# JVM 堆内存: 512M max, 128M initial (服务器共 961MB RAM, MySQL~80MB+Nginx~3MB)
ENTRYPOINT ["java", "-Xmx512m", "-Xms128m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
