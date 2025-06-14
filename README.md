# TermIt

TermIt is a [SKOS](https://www.w3.org/2004/02/skos/) compliant terminology management tool based on Semantic Web
technologies. It allows managing vocabularies consisting of thesauri and ontologies. It can also manage documents whose
content can be used to seed the vocabularies (e.g., normative documents with definition of domain terminology). In
addition, documents can also be analyzed to discover occurrences of the vocabulary terms.

## Terminology

#### Asset

An **asset** is an object of one of the main domain types managed by the system - _Resource_, _Term_ or _Vocabulary_.

## Required Technologies

- JDK 17 or newer
- Apache Maven 3.6.x or newer

## System Architecture

The system is split into two projects, [__TermIt__](https://github.com/kbss-cvut/termit) is the backend, [__TermIt
UI__](https://github.com/kbss-cvut/termit-ui) represents the frontend. Both projects are built separately and can run
separately.

See the [docs folder](doc/index.md) for additional information on implementation, setup, configuration and the
architectural decisions record.

## Technologies

This section briefly lists the main technologies and principles used (or planned to be used) in the application.

- Spring Boot 3, Spring Framework 6, Spring Security, Spring Data (paging, filtering)
- Jackson Databind
- [JB4JSON-LD](https://github.com/kbss-cvut/jb4jsonld-jackson) - Java - JSON-LD (de)serialization library
- [JOPA](https://github.com/kbss-cvut/jopa) - persistence library for the Semantic Web
- JUnit 5, Mockito 4, Hamcrest 2
- Jakarta Servlet API 4
- JSON Web Tokens
- SLF4J + Logback
- CORS (for separate frontend)
- Java bean validation (JSR 380)

## Ontologies

The ontology on which TermIt is based can be found in the `ontology` folder. It extends the
_popis-dat_ ontology (http://onto.fel.cvut.cz/ontologies/slovnik/agendovy/popis-dat). TermIt vocabularies and terms
use the SKOS vocabulary (http://www.w3.org/TR/skos-reference/skos.rdf).

Relevant ontologies need to be loaded into the repository for proper inference functionality.
See [setup.md](doc/setup.md)
for more details.

## Monitoring

[JavaMelody](https://github.com/javamelody/javamelody) can be used for monitoring the application and its usage. The
data are
available on the `/monitoring` endpoint and are secured using _basic_ authentication. Credentials are configured using
the `javamelody.init-parameters.authorized-users`
parameter in `application.yml` (see
the [JavaMelody Spring Boot Starter docs](https://github.com/javamelody/javamelody/wiki/SpringBootStarter)).

## Documentation

TermIt REST API is available for each instance via [Swagger UI](https://swagger.io/tools/swagger-ui/). It is accessible
at `http://SERVER_URL/PATH/swagger-ui/index.html`, where `SERVER_URL` is the URL of the server at which TermIt backend
is running and `PATH` is the context path. A link to the API documentation is also available in the footer of the TermIt
UI.

Build configuration and deployment is described in [setup.md](doc/setup.md).

## Docker

The Docker image of TermIt backend alone can be built by
`docker build -t termit-server .`

Then, TermIt can be run and exposed at the port 8080 as
`sudo docker run -e REPOSITORY_URL=<GRAPHDB_REPOSITORY_URL> -p 8080:8080 termit-server`

An optional argument is `<GRAPHDB_REPOSITORY_URL>` pointing to the RDF4J/GraphDB repository.

TermIt Docker images are also published to [DockerHub](https://hub.docker.com/r/kbsscvut/termit).

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
- [TermIt: Managing Normative Thesauri](https://content.iospress.com/articles/semantic-web/sw243547) - a journal paper
  we wrote about TermIt
    - Cite as _Křemen P, Med M., Blaško M., Saeeda L., Ledvinka M. and Buzek A. (2024). TermIt: Managing Normative
      Thesauri’. Semantic Web. DOI: 10.3233/SW-243547_

## Getting TermIt

Docker images of TermIt backend are available at [DockerHub](https://hub.docker.com/r/kbsscvut/termit).

## License

Licensed under GPL v3.0.
