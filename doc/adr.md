# Architectural Decisions Record

This file explains some of the important architectural decisions made during the development of TermIt, together with
possible implications and lessons learnt for future.

Status options are: _proposed_, _accepted_, _superseded_.

### Vocabulary Access Control
- **Status**: accepted
- **Date**: 2023-02-13

##### Context

It has been decided that access control should to vocabularies and terms should be implemented in greater detail than the current
global roles.

##### Considered Options

1. ACL-based only access control.
2. ACL combined with global roles supporting only users.
3. ACL combined with global roles supporting users and user groups.

##### Decision Outcome

Option **3** will be used. Global roles will be applicable mainly as defaults when creating a new vocabulary. Global admin role
also has master access to all vocabularies. In particular, the following access levels will be defined for each vocabulary:

1. `None` - vocabulary is not visible at all.
2. `Read` - user can perform only read operations on a vocabulary and its content (incl. export) and comments its terms.
3. `Write` - `Read` + user can edit vocabulary and its content, run text analysis on file and term definitions, create/edit files associated with vocabulary.
4. `Security` - `Write` + delete vocabulary, restore from SKOS (re-import), manage snapshots, remove file, manage user access levels.

In addition:
- When a new vocabulary is created, its author is automatically assigned `Security` access level. 
- User access level overrides group access level.
- When a new vocabulary is created, its author decides what the default access levels for editors and readers will be.
- Only TermIt admins are able to manage user groups.

##### Consequences

The `AuthorizationService` needs to be extended to support the ACL-based access control. Every access to a vocabulary or its
contents has to be validated by based on the ACL. Existing data prior to implementation of this have to be migrated to sensible
defaults or the `AuthorizationService` has to provided defaults when access control is not configured for a vocabulary.


### Session-based Editable Vocabularies
- **Status**: accepted
- **Date**: 2022-08-23

##### Context

The requirement to unify the base version of TermIt and the version used by the [OpenData MVCR project](https://github.com/opendata-mvcr)
resulted in the need to be able to specify only a subset of vocabularies as being editable. Moreover, such vocabularies
may not reside in a repository context identified by the vocabulary IRI, but may exist in a different context (working copy of the vocabulary).
TermIt is then provided with a set of vocabulary contexts that contain editable copies of selected vocabularies. All other vocabularies
are read-only.
TermIt needs to be able to remember this set of edited vocabularies over multiple client requests, so that they do not have to be provided
with every request.

##### Considered Options

1. Manually manage sessions, store session identifier as a claim in user's JWT
2. Let Spring manage sessions and store edited vocabularies in a `SessionScoped` bean
3. Do not store anything, make editable vocabularies request-scoped and require the client to supply editable vocabularies with every request

##### Decision Outcome

Selected option 2 due to its simplicity (built-in session management in Spring), and generality (no need to add claims to JWT which would be problematic
once Keycloak integration is supported).

###### Consequences

A consequence of using sessions is that browser clients (namely TermIt UI) have to send requests [with credentials](https://developer.mozilla.org/en-US/docs/Web/API/XMLHttpRequest/withCredentials),
which in turn means backend CORS configuration cannot use wildcards (allowed origins, allowed methods, allowed headers) anymore.
Since most deployments sit behind a proxy (Nginx or Apache HTTPD), this should not be a problem for them. However, remote access
to backend (e.g., TermIt UI running in dev mode on localhost against a remote TermIt) has to be configured. One part of this is
the `termit.cors.allowedOrigins` configuration parameter. The other is configuration of the server proxy. Apache HTTPD is quite
problematic in this area. For things to work, headers set by the proxy need to include the intended clients and allowed headers
cannot use a wildcard and needs instead to enumerate the allowed headers (otherwise, preflight requests do not work). An example
configuration may look something like:

```
Header set Access-Control-Allow-Origin "http://localhost:3000"
Header set Access-Control-Allow-Headers "Accept, Access-Control-Allow-Credentials, Access-Control-Allow-Origins, Access-Control-Allow-Headers, Authorization..."
Header set Access-Control-Allow-Credentials "true"
ProxyPass http://localhost:8080/termit nocanon timeout=6000 connectiontimeout=6000
ProxyPassReverse http://localhost:8080/termit
```
