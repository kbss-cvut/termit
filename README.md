# TermIt

TermIt is a [SKOS](https://www.w3.org/2004/02/skos/) compliant terminology management tool based on Semantic Web
technologies. It allows managing vocabularies consisting of thesauri and ontologies. It can also manage documents whose
content can be used to seed the vocabularies (e.g., normative documents with definition of domain terminology). In
addition, documents can also be analyzed to discover occurrences of the vocabulary terms.

## Terminology

#### Asset

An **asset** is an object of one of the main domain types managed by the system - _Resource_, _Term_ or _Vocabulary_.

## Required Technologies

- JDK 11 or newer
- Apache Maven 3.6.x or newer


## System Architecture

The system is split into two projects, [__TermIt__](https://github.com/kbss-cvut/termit) is the backend, [__TermIt
UI__](https://github.com/kbss-cvut/termit-ui) represents the frontend. Both projects are built separately and can run
separately.

See the [docs folder](doc/index.md) for additional information on implementation, setup, configuration and the architectural decisions record.


## Technologies

This section briefly lists the main technologies and principles used (or planned to be used) in the application.

- Spring Boot 2, Spring Framework 5, Spring Security, Spring Data (paging, filtering)
- Jackson 2.13
- [JB4JSON-LD](https://github.com/kbss-cvut/jb4jsonld-jackson)* - Java - JSON-LD (de)serialization library
- [JOPA](https://github.com/kbss-cvut/jopa) - persistence library for the Semantic Web
- JUnit 5* (RT used 4), Mockito 4* (RT used 1), Hamcrest 2* (RT used 1)
- Servlet API 4* (RT used 3.0.1)
- JSON Web Tokens* (CSRF protection not necessary for JWT)
- SLF4J + Logback
- CORS* (for separate frontend)
- Java bean validation (JSR 380)*

_* Technology not used in [INBAS RT](https://github.com/kbss-cvut/reporting-tool)_


## Ontology

The ontology on which TermIt is based can be found in the `ontology` folder. For proper inference
functionality, `termit-model.ttl`, the
_popis-dat_ ontology model (http://onto.fel.cvut.cz/ontologies/slovnik/agendovy/popis-dat/model) and the SKOS vocabulary
model
(http://www.w3.org/TR/skos-reference/skos.rdf) need to be loaded into the repository used by TermIt (see `doc/setup.md`)
for details.

## Monitoring

We use [JavaMelody](https://github.com/javamelody/javamelody) for monitoring the application and its usage. The data are
available on the `/monitoring` endpoint and are secured using _basic_ authentication. Credentials are configured using
the `javamelody.init-parameters.authorized-users`
parameter in `application.yml` (see
the [JavaMelody Spring Boot Starter docs](https://github.com/javamelody/javamelody/wiki/SpringBootStarter)).

## Documentation

TermIt REST API is tentatively documented on [SwaggerHub](https://app.swaggerhub.com/apis/ledvima1/TermIt/) under the
appropriate version.

Build configuration and deployment is described in [setup.md](doc/setup.md).

## Dockerization

The docker image of TermIt backend can be built by
`docker build -t termit-server .`

Then, TermIt can be run and exposed at the port 8080 as
`sudo docker run -e REPOSITORY_URL=<GRAPHDB_REPOSITORY_URL> -p 8080:8080 termit-server`

An optional argument is `<GRAPHDB_REPOSITORY_URL>` pointing to the RDF4J/GraphDB repository.

## Links

- [TermIt UI](https://github.com/kbss-cvut/termit-ui) - repository with TermIt frontend source code
- [TermIt Docker](https://github.com/kbss-cvut/termit-docker) - repository with Docker configuration of the whole TermIt
  system (including the text analysis service and data repository)
- [TermIt Web](http://kbss-cvut.github.io/termit-web) - contains some additional information and tutorials
- [TermIt: A Practical Semantic Vocabulary Manager](https://www.scitepress.org/Papers/2020/95637/95637.pdf) - a
  conference paper we wrote about TermIt
    - Cite as _Ledvinka M., Křemen P., Saeeda L. and Blaško M. (2020). TermIt: A Practical Semantic Vocabulary
      Manager.In Proceedings of the 22nd International Conference on Enterprise Information Systems - Volume 1: ICEIS,
      ISBN 978-989-758-423-7, pages 759-766. DOI: 10.5220/0009563707590766_

## License

Licensed under GPL v3.0.
