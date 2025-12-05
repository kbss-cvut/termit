# Implementation Notes

This file contains implementation notes. It is intended for developers of TermIt or anyone interested in its internal
structure and logic.

### jsoup

Had to switch from standard Java DOM implementation to **jsoup** because DOM had sometimes trouble parsing HTML
documents (`meta` tags in header). Jsoup, on the other hand, handles HTML natively and should be able to work with XML (
if need be) as well.

### Validation

The application uses JSR 380 validation API. This provides a generic, easy-to-use API for bean validation based on
annotations. Use it to verify input data. See `User` and its validation in `BaseRepositoryService`
/`UserRepositoryService`.
`ValidationException` is then handled by `RestExceptionHandler` and an appropriate response is returned to the client.

### Storage

TermIt is preconfigured to run against a local GraphDB repository at `http://locahost:7200/repositories/termit`. This
can be changed by updating `application.yml`.

### User vs. UserAccount

`User` is a domain class used for domain functions, mostly for resource provenance (author, last editor). It does not
support password.
`UserAccount` is used for security-related functions and supports password. Most parts of the application **should** use
`User`.

### JMX

A JMX bean called `AppAdminBean` is published by TermIt. It provides several maintenance functions, such as invalidating
the application caches.

### Fulltext Search

Fulltext search is implemented using Lucene connectors in GraphDB.
Connectors are automatically managed by `GraphDBLuceneConnectorInitializer`.
The initializer creates connectors from definitions in `src/main/resources/lucene`
for all languages used by indexed fields.

### RDFS Inference in Tests

The test in-memory repository is configured to be a RDF4J SAIL with RDFS inferencing engine. The repository is by default left
empty (without the model) to facilitate test performance (inference in RDF4J is really slow). To load the
TermIt model into the repository and thus enable RDFS inference, call the `enableRdfsInference`
method available on both `BaseDaoTestRunner` and `BaseServiceTestRunner`.
