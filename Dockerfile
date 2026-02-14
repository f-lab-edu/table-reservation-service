FROM eclipse-temurin:21-jre
COPY build/libs/*.jar app.jar
ENV TZ=Asia/Seoul
ENTRYPOINT ["java", "-jar", "/app.jar", "--spring.profiles.active=stress-test"]
