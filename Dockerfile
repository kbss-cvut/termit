FROM maven:3-openjdk-11 as build

WORKDIR /termit

COPY pom.xml pom.xml

RUN mvn -B dependency:resolve

COPY ontology ontology
COPY profile profile
COPY src src

RUN mvn package -B -P standalone -DskipTests=true

FROM openjdk:11-jdk-oracle as runtime
COPY --from=build  /termit/target/termit.jar termit.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/termit.jar"]
