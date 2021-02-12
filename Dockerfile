FROM maven:3.6.1-jdk-8 as maven_builder
ARG REPOSITORY_URL=http://localhost:7200/repositories/termit
ARG LANGUAGE=cs
ARG NAMESPACE_VOCABULARY=http://onto.fel.cvut.cz/ontologies/slovnik/
ARG NAMESPACE_USER=http://onto.fel.cvut.cz/ontologies/uzivatel/
ARG NAMESPACE_RESOURCE=http://onto.fel.cvut.cz/ontologies/zdroj/
ARG TEXT_ANALYSIS_URL=http://localhost/annotace/annotate
ARG DEPLOYMENT=NOT-SPECIFIED

ENV TERMIT_HOME=/backend
ENV FILE_STORAGE=$TERMIT_HOME/storage
WORKDIR $TERMIT_HOME
ADD . $TERMIT_HOME
COPY src/main/scripts/wait-for-it.sh $TERMIT_HOME/
RUN sed -E -i "s@repository[.]url=(.*)@repository.url=$REPOSITORY_URL@g" src/main/resources/config.properties
RUN sed -E -i "s@persistence[.]language=(.*)@persistence.language=$LANGUAGE@g" src/main/resources/config.properties
RUN sed -E -i "s@namespace[.]vocabulary=(.*)@namespace.vocabulary=$NAMESPACE_VOCABULARY@g" src/main/resources/config.properties
RUN sed -E -i "s@namespace[.]user=(.*)@namespace.user=$NAMESPACE_USER@g" src/main/resources/config.properties
RUN sed -E -i "s@namespace[.]resource=(.*)@namespace.resource=$NAMESPACE_RESOURCE@g" src/main/resources/config.properties
RUN sed -E -i "s@file[.]storage=(.*)@file.storage=$FILE_STORAGE@g" src/main/resources/config.properties
RUN sed -E -i "s@textAnalysis[.]url=(.*)@textAnalysis.url=$TEXT_ANALYSIS_URL@g" src/main/resources/config.properties
RUN mvn clean package -DskipTests=true -B -T 2C -P production,graphdb "-Ddeployment=$DEPLOYMENT"
FROM tomcat:9-jdk8-corretto
COPY --from=maven_builder /backend/target/termit.war /usr/local/tomcat/webapps
