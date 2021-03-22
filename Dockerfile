FROM openjdk:11-jdk-oracle

ARG JAR_FILES="target/*.jar"
ENV JAR=termit.jar
COPY ${JAR_FILES} $JAR

EXPOSE 8080

ENTRYPOINT ["java","-jar","/termit.jar"]