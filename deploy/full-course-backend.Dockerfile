ARG BASE_IMAGE=eclipse-temurin:21-jre
FROM ${BASE_IMAGE}
WORKDIR /app
# Pinned upstream's canonical executable artifact; never a stale candidate jar glob.
COPY target/course-project-backend-0.0.1-SNAPSHOT.jar /app/course.jar
ENV JAVA_OPTS="-Xms128m -Xmx768m" TZ=Asia/Shanghai
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/course.jar"]
