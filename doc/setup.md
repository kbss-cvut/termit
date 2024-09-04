# TermIt Setup Guide

This guide provides information on how to build and deploy TermIt.

## Build

### System Requirements

* JDK 11 or newer (tested up to JDK 11 LTS)
* Apache Maven 3.5.x or newer


### Setup

#### Maven Profiles

To build TermIt for **non**-development deployment, use Maven and select the `production` profile.

In addition, full text search in TermIt supports three modes:
1. Default label-based substring matching
2. RDF4J repository with Lucene index
3. GraphDB repository with Lucene index

Options 2. and 3. have their respective Maven profiles - `rdf4j` and `graphdb`. Select one of them
or let the system use the default one.

Moreover, TermIt can be packaged either as an executable JAR (using Spring Boot) or as a WAR that can be deployed in any Servlet API 4-compatible application server.
Maven profiles `standalone` (active by default) and `war` can be used to activate them respectively.

#### Application Configuration

The rest of the configuration is done in the `application.yml` file in `src/main/resources`.

Most of the parameters there should be self-explanatory or have documentation in the `Configuration` class. 

Note that for proper functionality of the frontend, `termit.cors.allowedOrigins` must be set to the host and port from which the 
`termi-ui` accesses TermIt backend. This parameter defaults to `http://localhost:3000` but that is usable only for local development.

There is one parameter not used by the application itself, but by Spring - `spring.profiles.active`. There are several Spring profiles currently used
by the application:
* `lucene` - decides whether Lucene text indexing is enabled and should be used in full text search queries.
* `admin-registration-only` - decides whether new users can be registered only by application admin, or whether anyone can register.
* `no-cache` - disables EhCache which is used to cache lists of resources and vocabularies for faster retrieval.

The `lucene` Spring profile is activated automatically by the `rdf4j` and `graphdb` Maven profiles. `admin-registration-only` and `no-cache` have to be added
either in `application.yml` directly, or one can pass the parameter to Maven build, e.g.:

* `mvn clean package -P graphdb "-Dspring.profiles.active=lucene,admin-registration-only"`


#### Example

* `mvn clean package -B -P production,graphdb "-Ddeployment=DEV"`
* `clean package -B -P production,rdf4j,war "-Ddeployment=STAGE"`

The `deployment` parameter is used to parameterize log messages and JMX beans and is important in case multiple deployments
of TermIt are running in the same Tomcat.


#### Building on Windows

Building TermIt on Windows sometimes requires Maven to be configured to use the **UTF-8** encoding. In certain cases, 
Maven would otherwise use the default Windows encoding (e.g., CP-1250) which can cause issues when accessing URLs with Czech accents 
(e.g. the `ontology/termit-glosář.ttl` file required for generating application vocabulary).

This configuration can be done, for example, via the `MAVEN_OPTS` environmental variable. Either set it temporarily by calling

* `set MAVEN_OPTS= -Dfile.encoding="UTF-8"`

or configure it permanently by setting the `MAVEN_OPTS` variable in System Settings.


## Deployment

### System Requirements

* JDK 11 or later (tested with JDK 11)
* (WAR) Apache Tomcat 8.5 or 9.x (recommended) or any Servlet API 4-compatible application server
  * _For deployment of a WAR build artifact._
  * Do not use Apache Tomcat 10.x, it is based on the new Jakarta EE and TermIt would not work on it due to package namespace issues (`javax` -> `jakarta`)

### Setup

Application deployment is simple - just deploy the WAR file (in case of the `war` Maven build profile) to an 
application server or run the JAR file (in case of the `standalone` Maven build profile).

What is important is the correct setup of the repository. We will describe two options:

1. GraphDB
2. RDF4J

#### GraphDB

In order to support inference used by the application, a custom ruleset has to be specified for the TermIt repository.

1. Start by creating a GraphDB repository with custom ruleset
2. Use the ruleset provided in TermIt at `rulesets/rules-termit-graphdb.pie`
3. Create the repository, configure it as you like (ensure the repository ID matches TermIt repository configuration)
4. Create the following Lucene connectors in GraphDB:
    * *Label index*
        * name: **label_index**
        * Field name: **label**, **title** 
        * Property chain: **http://www.w3.org/2000/01/rdf-schema#label**, **http://purl.org/dc/terms/title**
        * Languages: _Leave empty (for indexing all languages) or specify the language tag - see below_
        * Types: **http://www.w3.org/2004/02/skos/core#Concept**, **http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/slovník**
        * Analyzer: Analyzer appropriate for the system language, e.g. **org.apache.lucene.analysis.cz.CzechAnalyzer**
    * *Definition and comment index*
        * name: **defcom_index**
        * Field name: **definition**, **comment**, **description**
        * Languages: _Leave empty (for indexing all languages) or specify the language tag - see below_
        * Property chain: **http://www.w3.org/2004/02/skos/core#definition**, **http://www.w3.org/2000/01/rdf-schema#comment**, **http://purl.org/dc/terms/description**
        * Types and Analyzer as above
        
Language can be set for each connector. This is useful in case the data contain labels, definitions, and comments in multiple languages. In this case,
if connector language is not specified, FTS results will contain text snippets with text in all the available languages. For instance, assume
there is a term with label `území`@cs and `area`@en. Now, if no language is specified for the label connector, the resulting text snippet may
look as follows: `<em>území</em> area`, which may not be desired. If the connector language is set to `cs`, the result snippet will contain
only `<em>území</em>`. See the [documentation](http://graphdb.ontotext.com/documentation/free/lucene-graphdb-connector.html) for more details.

#### RDF4J

In order to support the inference used by the application, new rules need to be added to RDF4J because its own RDFS rule engine does not
support OWL stuff like inverse properties (which are used in the model).

For RDF4J 2.x: 
1. Start by creating an RDF4J repository of type **RDFS+SPIN with Lucene support**
2. Upload SPIN rules from `rulesets/rules-termit-spin.ttl` into the repository
3. There is no need to configure Lucene connectors, it by default indexes all properties in RDF4J (alternatively, it is possible
to upload a repository configuration directly into the system repository - see examples at [[1]](https://github.com/eclipse/rdf4j/tree/master/core/repository/api/src/main/resources/org/eclipse/rdf4j/repository/config)
4. -----

For RDF4J 3.x: 
1. Start by creating an RDF4J repository with RDFS and SPIN inference and Lucene support
    * Copy repository configuration into the appropriate directory, as described at [[2]](https://rdf4j.eclipse.org/documentation/server-workbench-console/#repository-configuration)
    * Native store with RDFS+SPIN and Lucene sample configuration is at [[3]](https://github.com/eclipse/rdf4j/blob/master/core/repository/api/src/main/resources/org/eclipse/rdf4j/repository/config/native-spin-rdfs-lucene.ttl)
2. Upload SPIN rules from `rulesets/rules-termit-spin.ttl` into the repository
3. There is no need to configure Lucene connectors, it by default indexes all properties in RDF4J
4. -----

#### Common

TermIt needs the repository to provide some inference. Beside loading the appropriate rulesets (see above), it is also
necessary to load the ontological models into the repository.

5. Upload the following RDF files into the newly created repository:
    * `ontology/termit-glosář.ttl`
    * `ontology/termit-model.ttl`
    * `http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/model`
    * `http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/glosář`
    * `https://www.w3.org/TR/skos-reference/skos.rdf`

6. Deploy TermIt. It will generate a default admin account, write its credentials to standard output and into a hidden file in the current user's home.

For proper functionality of the text analysis service, [Annotace](https://github.com/kbss-cvut/annotace) has to be running and its URL configured in `application.yml`.


#### Repository Credentials

Note that if the repository server to which TermIt is connecting is secured (**strongly recommended**), it is necessary to put the repository user 
username and password into `application.yml` so that TermIt persistence can access the repository. The configuration parameter are in `Configuration.Repository` class,
but here's a quick example:

```
termit:
    repository:
        url: http://localhost:7200/repositories/termit
        username: termit
        password: supersecretpassword
```

### Authentication

TermIt can operate in two authentication modes:

1. Internal authentication
2. OAuth2 based (e.g. [Keycloak](https://www.keycloak.org/))

By default, OAuth2 is disabled and internal authentication is used  
To enable it, set termit security provider to `oidc`
and provide issuer-uri and jwk-set-uri.

**`application.yml` example:**
```yml
spring:
    security:
        oauth2:
            resourceserver:
                jwt:
                    issuer-uri: http://keycloak.lan/realms/termit
                    jwk-set-uri: http://keycloak.lan/realms/termit/protocol/openid-connect/certs
termit:
    security:
        provider: "oidc"
```

**Environmental variables example:**
```
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI=http://keycloak.lan/realms/termit
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI=http://keycloak.lan/realms/termit/protocol/openid-connect/certs
TERMIT_SECURITY_PROVIDER=oidc
```

TermIt will automatically configure its security accordingly
(it is using Spring's [`ConditionalOnProperty`](https://www.baeldung.com/spring-conditionalonproperty)).

**Note that termit-ui needs to be configured for mathcing authentication mode.**
