# ---- Stage 1: Build frontend ----
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/node:24 AS frontend-builder
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com
COPY frontend/ ./
RUN npm run build

# ---- Stage 2: Build JAR ----
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/maven:3.9.14-eclipse-temurin-25 AS backend-builder
WORKDIR /build
COPY pom.xml ./
COPY .mvn/settings.xml /root/.m2/settings.xml
RUN mvn -q dependency:go-offline -B
COPY src ./src
COPY --from=frontend-builder /build/dist ./src/main/resources/static
RUN mvn -q -B package -DskipTests

# ---- Stage 3: Runtime ----
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/openjdk:25-jdk-slim
WORKDIR /app

ENV API_CONVERT_SQLITE_PATH=/app/data/api-convert.db \
    API_CONVERT_TIME_ZONE=Asia/Shanghai \
    TZ=Asia/Shanghai \
    JAVA_OPTS=""

EXPOSE 8080
COPY --from=backend-builder /build/target/api-convert-*.jar /app/api-convert.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/api-convert.jar"]
