# ---------- 构建阶段：一次构建指定模块及其依赖 ----------
FROM maven:3.9-eclipse-temurin-21 AS build

ARG MODULE
WORKDIR /workspace

# 先复制 POM，利用 Docker 层缓存；源码变化时只重跑 package。
COPY pom.xml ./
COPY fcrc-common/pom.xml fcrc-common/pom.xml
COPY fcrc-service-parent/pom.xml fcrc-service-parent/pom.xml
COPY fcrc-gateway/pom.xml fcrc-gateway/pom.xml
COPY fcrc-customer/pom.xml fcrc-customer/pom.xml
COPY fcrc-risk/pom.xml fcrc-risk/pom.xml
COPY fcrc-compliance/pom.xml fcrc-compliance/pom.xml
COPY fcrc-case/pom.xml fcrc-case/pom.xml
COPY fcrc-report/pom.xml fcrc-report/pom.xml
COPY fcrc-workflow/pom.xml fcrc-workflow/pom.xml
COPY fcrc-agent/pom.xml fcrc-agent/pom.xml

# 复制全部源码后只打包目标模块。教学项目追求“单条命令可复现”。
COPY . .
RUN mvn -B -pl "${MODULE}" -am package -DskipTests

# ---------- 运行阶段：最小 JRE + 非 root 用户 ----------
FROM eclipse-temurin:21-jre-jammy

ARG MODULE
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Dfile.encoding=UTF-8"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system fcrc \
    && useradd --system --gid fcrc --home-dir /app --shell /usr/sbin/nologin fcrc

WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${MODULE}-1.0.0.jar /app/app.jar

RUN mkdir -p /app/data && chown -R fcrc:fcrc /app
USER fcrc

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
