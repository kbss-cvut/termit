FROM maven:3.6.1-jdk-8 as maven_builder
ARG REPOSITORY_URL=http://localhost:7200/repositories/termit
ENV TERMIT_HOME=/backend
WORKDIR $TERMIT_HOME
ADD . $TERMIT_HOME
COPY src/main/scripts/wait-for-it.sh $TERMIT_HOME/
RUN sed -E -i "s@repository[.]url=(.*)@repository.url=$REPOSITORY_URL@g" src/main/resources/config.properties
RUN mvn clean install -T 2C -DskipTests=true

FROM tomcat:9-jdk8-corretto
COPY --from=maven_builder /backend/target/termit.war /usr/local/tomcat/webapps
