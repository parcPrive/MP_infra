# Java 17 런타임만 포함된 이미지에서 빌드된 Spring Boot JAR를 실행합니다.
FROM eclipse-temurin:17-jre

WORKDIR /app

# ./gradlew bootJar로 만든 JAR를 컨테이너 내부 app.jar로 복사합니다.
COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
