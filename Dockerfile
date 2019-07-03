FROM openjdk:8-jdk-alpine
VOLUME /tmp
COPY /build/libs/*.jar webapp/sentiment.jar
WORKDIR /webapp
EXPOSE 8080
ENTRYPOINT ["java","-jar","sentiment.jar"]
