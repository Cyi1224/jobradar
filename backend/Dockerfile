# ── 构建阶段：用 Maven 打包（每次 build 都重新编译最新源码）──
# 注意：docker compose build 时若不加 --no-cache，mvn clean package 仍会执行，
#       因为 COPY src 后源码变化会导致该层失效重建。确保 src 是最新代码即可。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# 依赖层：单独缓存，仅 pom.xml 变化时重新拉取
RUN mvn -B -q dependency:go-offline
COPY src ./src
# 关键：clean 强制全新编译，不含旧 class
RUN mvn -B -q clean package -DskipTests

# ── 运行阶段：精简 JRE ──
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# JVM 堆内存: 512M max, 128M initial (服务器共 961MB RAM, MySQL~80MB+Nginx~3MB)
ENTRYPOINT ["java", "-Xmx512m", "-Xms128m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
