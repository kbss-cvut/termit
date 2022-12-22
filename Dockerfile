FROM maven:3-eclipse-temurin-11 as build

WORKDIR /termit

COPY pom.xml pom.xml

RUN mvn -B de.qaware.maven:go-offline-maven-plugin:resolve-dependencies

COPY ontology ontology
COPY profile profile
COPY src src

RUN mvn package -B -P graphdb,standalone -DskipTests=true

FROM eclipse-temurin:11-jdk-alpine as runtime
COPY --from=build  /termit/target/termit.jar termit.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/termit.jar"]
