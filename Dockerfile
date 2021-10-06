FROM maven:3-openjdk-11 as build

COPY . /termit
WORKDIR /termit
RUN mvn package -B -P graphdb,standalone,no-cache

FROM openjdk:11-jdk-oracle as runtime
COPY --from=build  /termit/target/termit.jar termit.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/termit.jar"]
