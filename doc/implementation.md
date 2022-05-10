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

### User vs UserAccount

`User` is a domain class used for domain functions, mostly for resource provenance (author, last editor). It does not
support password.
`UserAccount` is used for security-related functions and supports password. Most parts of the application **should** use
`User`.

### JMX

A JMX bean called `AppAdminBean` was added to the application. Currently, it supports invalidation of application
caches. The bean's name is set during Maven build. In case multiple deployments of TermIt are running on the same
application server, it is necessary to provide different names for it. A Maven property with default value _DEV_ was
introduced for it. To specify a different value, pass a command line parameter to Maven, so the build call might look as
follows:

`mvn clean package -B -P production "-Ddeployment=DEV"`

### Fulltext Search

Fulltext search currently supports multiple types of implementation:

* Simple substring matching on term and vocabulary label _(default)_
* RDF4J with Lucene SAIL
* GraphDB with Lucene connector

Each implementation has its own search query which is loaded and used by `SearchDao`. In order for the more advanced
implementations for Lucene to work, a corresponding Maven profile (**graphdb**, **rdf4j**) has to be selected. This
inserts the correct query into the resulting artifact during build. If none of the profiles is selected, the default
search is used.

Note that in case of GraphDB, corresponding Lucene connectors (`label_index` for labels and `defcom_index` for
definitions and comments)
have to be created as well.

### RDFS Inference in Tests

The test in-memory repository is configured to be a SPIN SAIL with RDFS inferencing engine. Thus, basically all the
inference features available in production are available in tests as well. However, the repository is by default left
empty (without the model or SPIN rules) to facilitate test performance (inference in RDF4J is really slow). To load the
TermIt model into the repository and thus enable RDFS inference, call the `enableRdfsInference`
method available on both `BaseDaoTestRunner` and `BaseServiceTestRunner`. SPIN rules are currently not loaded as they
don't seem to be used by any tests.
