# Java 17 런타임만 들어있는 가벼운 베이스 이미지입니다.
# Gradle이나 소스코드는 이미지 안에 넣지 않고, 이미 빌드된 JAR만 실행합니다.
FROM eclipse-temurin:17-jre

# 컨테이너 내부에서 애플리케이션을 실행할 작업 디렉터리입니다.
WORKDIR /app

# Docker build context 안의 build/libs/*.jar 파일을 컨테이너의 /app/app.jar로 복사합니다.
# 이 프로젝트에서는 먼저 ./gradlew bootJar로 JAR를 만든 뒤 Docker 이미지를 빌드합니다.
COPY build/libs/*.jar app.jar

# 문서 역할의 포트 선언입니다. 실제 포트 연결은 docker run -p 또는 compose ports가 담당합니다.
EXPOSE 8080

# 컨테이너가 시작될 때 실행되는 명령입니다.
ENTRYPOINT ["java", "-jar", "app.jar"]
