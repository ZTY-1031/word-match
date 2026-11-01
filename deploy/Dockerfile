# 运行镜像（jar 已在本地打好，这里只负责跑）
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY backend/target/word-match-0.1.0.jar /app/app.jar

ENV TZ=Asia/Shanghai
EXPOSE 8080

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Xms128m", "-Xmx256m", \
            "-jar", "/app/app.jar", "--server.port=8080"]
