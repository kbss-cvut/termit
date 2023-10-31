/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.util;

import cz.cvut.kbss.termit.model.acl.AccessLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;

import java.util.Optional;
import java.util.Set;

/**
 * Represents application-wide configuration.
 * <p>
 * The runtime configuration consists of predefined default values and configuration loaded from config files on
 * classpath. Values from config files supersede the default values.
 * <p>
 * The configuration can be also set via <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables">OS
 * environment variables</a>. These override any statically configured values.
 */
@ConfigurationProperties("termit")
@Primary
@Valid
public class Configuration {
    /**
     * TermIt frontend URL.
     * <p>
     * It is used, for example, for links in emails sent to users.
     */
    private String url = "http://localhost:3000/#";
    /**
     * Name of the JMX bean exported by TermIt.
     * <p>
     * Normally should not need to change unless multiple instances of TermIt are running in the same application
     * server.
     */
    private String jmxBeanName = "TermItAdminBean";
    private Persistence persistence = new Persistence();
    private Repository repository = new Repository();
    private ChangeTracking changetracking = new ChangeTracking();
    private Comments comments = new Comments();
    private Namespace namespace = new Namespace();
    private Admin admin = new Admin();
    private File file = new File();
    private Jwt jwt = new Jwt();
    private TextAnalysis textAnalysis = new TextAnalysis();
    private Glossary glossary = new Glossary();
    private PublicView publicView = new PublicView();
    private Workspace workspace = new Workspace();
    private Cors cors = new Cors();
    private Schedule schedule = new Schedule();
    private ACL acl = new ACL();
    private Mail mail = new Mail();
    private Security security = new Security();
    private Language language = new Language();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getJmxBeanName() {
        return jmxBeanName;
    }

    public void setJmxBeanName(String jmxBeanName) {
        this.jmxBeanName = jmxBeanName;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public void setPersistence(Persistence persistence) {
        this.persistence = persistence;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public ChangeTracking getChangetracking() {
        return changetracking;
    }

    public void setChangetracking(ChangeTracking changetracking) {
        this.changetracking = changetracking;
    }

    public Comments getComments() {
        return comments;
    }

    public void setComments(Comments comments) {
        this.comments = comments;
    }

    public Namespace getNamespace() {
        return namespace;
    }

    public void setNamespace(Namespace namespace) {
        this.namespace = namespace;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public TextAnalysis getTextAnalysis() {
        return textAnalysis;
    }

    public void setTextAnalysis(TextAnalysis textAnalysis) {
        this.textAnalysis = textAnalysis;
    }

    public Glossary getGlossary() {
        return glossary;
    }

    public void setGlossary(Glossary glossary) {
        this.glossary = glossary;
    }

    public PublicView getPublicView() {
        return publicView;
    }

    public void setPublicView(PublicView publicView) {
        this.publicView = publicView;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    public ACL getAcl() {
        return acl;
    }

    public void setAcl(ACL acl) {
        this.acl = acl;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }


    public static class Persistence {
        /**
         * OntoDriver class for the repository.
         */
        @NotNull
        String driver;
        /**
         * Language used to store strings in the repository (persistence unit language).
         */
        @NotNull
        String language;

        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }

    public static class Repository {
        /**
         * URL of the main application repository.
         */
        @NotNull
        String url;
        /**
         * Public URL of the main application repository.
         * <p>
         * Can be used to provide read-only no authorization access to the underlying data.
         */
        Optional<String> publicUrl = Optional.empty();
        /**
         * Username for connecting to the application repository.
         */
        String username;
        /**
         * Password for connecting to the application repository.
         */
        String password;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Optional<String> getPublicUrl() {
            return publicUrl;
        }

        public void setPublicUrl(Optional<String> publicUrl) {
            this.publicUrl = publicUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class ChangeTracking {
        Context context = new Context();

        public Context getContext() {
            return context;
        }

        public void setContext(Context context) {
            this.context = context;
        }

        public static class Context {
            /**
             * Extension appended to asset identifier (presumably a vocabulary ID) to denote its change tracking context
             * identifier.
             */
            @NotNull
            String extension;

            public String getExtension() {
                return extension;
            }

            public void setExtension(String extension) {
                this.extension = extension;
            }
        }
    }

    public static class Comments {
        /**
         * IRI of the repository context used to store comments (discussion to assets).
         */
        @NotNull
        String context;

        public String getContext() {
            return context;
        }

        public void setContext(String context) {
            this.context = context;
        }
    }

    public static class Namespace {
        /**
         * Namespace for vocabulary identifiers.
         */
        @NotNull
        String vocabulary;
        /**
         * Namespace for user identifiers.
         */
        @NotNull
        String user;
        /**
         * Namespace for resource identifiers.
         */
        @NotNull
        String resource;
        /**
         * Separator of Term namespace from the parent Vocabulary identifier.
         * <p>
         * Since Term identifier is given by the identifier of the Vocabulary it belongs to and its own normalized
         * label, this separator is used to (optionally) configure the Term identifier namespace.
         * <p>
         * For example, if we have a Vocabulary with IRI {@code http://www.example.org/ontologies/vocabularies/metropolitan-plan}
         * and a Term with normalized label {@code inhabited-area}, the resulting IRI will be {@code
         * http://www.example.org/ontologies/vocabularies/metropolitan-plan/SEPARATOR/inhabited-area}, where 'SEPARATOR'
         * is the value of this configuration parameter.
         */
        private NamespaceDetail term = new NamespaceDetail();
        /**
         * Separator of File namespace from the parent Document identifier.
         * <p>
         * Since File identifier is given by the identifier of the Document it belongs to and its own normalized label,
         * this separator is used to (optionally) configure the File identifier namespace.
         * <p>
         * For example, if we have a Document with IRI {@code http://www.example.org/ontologies/resources/metropolitan-plan/document}
         * and a File with normalized label {@code main-file}, the resulting IRI will be {@code
         * http://www.example.org/ontologies/resources/metropolitan-plan/document/SEPARATOR/main-file}, where
         * 'SEPARATOR' is the value of this configuration parameter.
         */
        private NamespaceDetail file = new NamespaceDetail();

        /**
         * Separator of snapshot timestamp and original asset identifier.
         * <p>
         * For example, if we have a Vocabulary with IRI {@code http://www.example.org/ontologies/vocabularies/metropolitan-plan}
         * and the snapshot separator is configured to {@code version}, a snapshot will IRI will look something like
         * {@code http://www.example.org/ontologies/vocabularies/metropolitan-plan/version/20220530T202317Z}.
         */
        private NamespaceDetail snapshot = new NamespaceDetail();

        public String getVocabulary() {
            return vocabulary;
        }

        public void setVocabulary(String vocabulary) {
            this.vocabulary = vocabulary;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public NamespaceDetail getTerm() {
            return term;
        }

        public void setTerm(NamespaceDetail term) {
            this.term = term;
        }

        public NamespaceDetail getFile() {
            return file;
        }

        public void setFile(NamespaceDetail file) {
            this.file = file;
        }

        public NamespaceDetail getSnapshot() {
            return snapshot;
        }

        public void setSnapshot(NamespaceDetail snapshot) {
            this.snapshot = snapshot;
        }

        public static class NamespaceDetail {
            @NotNull
            String separator;

            public String getSeparator() {
                return separator;
            }

            public void setSeparator(String separator) {
                this.separator = separator;
            }
        }
    }

    public static class Admin {
        /**
         * Specifies the folder in which admin credentials are saved when its account is generated.
         */
        @NotNull
        String credentialsLocation;
        /**
         * Name of the file in which admin credentials are saved when its account is generated.
         */
        @NotNull
        String credentialsFile;

        public String getCredentialsFile() {
            return credentialsFile;
        }

        public void setCredentialsFile(String credentialsFile) {
            this.credentialsFile = credentialsFile;
        }

        public String getCredentialsLocation() {
            return credentialsLocation;
        }

        public void setCredentialsLocation(String credentialsLocation) {
            this.credentialsLocation = credentialsLocation;
        }
    }

    public static class File {
        /**
         * Specifies root directory in which document files are stored.
         */
        @NotNull
        String storage;

        public String getStorage() {
            return storage;
        }

        public void setStorage(String storage) {
            this.storage = storage;
        }
    }

    public static class Jwt {
        /**
         * Secret key used when hashing a JWT.
         */
        @NotNull
        String secretKey;

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }

    public static class TextAnalysis {
        /**
         * URL of the text analysis service.
         */
        String url;

        /**
         * Minimal match score of a term occurrence for which a term assignment should be automatically generated.
         * <p>
         * More specifically, when annotated file content is being processed, term occurrences with sufficient score
         * will cause creation of corresponding term assignments to the file.
         *
         * @deprecated This configuration is currently not used.
         */
        @Deprecated
        @NotNull
        String termAssignmentMinScore;

        /**
         * Score threshold for a term occurrence for it to be saved into the repository.
         */
        @NotNull
        String termOccurrenceMinScore = Constants.SCORE_THRESHOLD.toString();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getTermAssignmentMinScore() {
            return termAssignmentMinScore;
        }

        public void setTermAssignmentMinScore(String termAssignmentMinScore) {
            this.termAssignmentMinScore = termAssignmentMinScore;
        }

        public String getTermOccurrenceMinScore() {
            return termOccurrenceMinScore;
        }

        public void setTermOccurrenceMinScore(String termOccurrenceMinScore) {
            this.termOccurrenceMinScore = termOccurrenceMinScore;
        }
    }

    public static class Glossary {
        /**
         * IRI path to append to vocabulary IRI to get glossary identifier.
         */
        @NotNull
        String fragment;

        public String getFragment() {
            return fragment;
        }

        public void setFragment(String fragment) {
            this.fragment = fragment;
        }
    }

    public static class PublicView {
        /**
         * Unmapped properties allowed to appear in the SKOS export.
         */
        @NotNull
        private Set<String> whiteListProperties;

        public Set<String> getWhiteListProperties() {
            return whiteListProperties;
        }

        public void setWhiteListProperties(final Set<String> whiteListProperties) {
            this.whiteListProperties = whiteListProperties;
        }
    }

    public static class Workspace {

        /**
         * Whether all vocabularies in the repository are editable.
         * <p>
         * Allows running TermIt in two modes - one is that all vocabularies represent the current version and can be
         * edited. The other mode is that working copies of vocabularies are created and the user only selects a subset
         * of these working copies to edit (the so-called workspace), while all other vocabularies are read-only for
         * them.
         */
        @NotNull
        private boolean allVocabulariesEditable = true;

        public boolean isAllVocabulariesEditable() {
            return allVocabulariesEditable;
        }

        public void setAllVocabulariesEditable(boolean allVocabulariesEditable) {
            this.allVocabulariesEditable = allVocabulariesEditable;
        }
    }

    public static class Cors {
        /**
         * A comma-separated list of allowed origins for CORS.
         */
        @NotNull
        private String allowedOrigins = "http://localhost:3000";

        /**
         * A comma-separated list of allowed origin patterns for CORS.
         * <p>
         * This allows a more dynamic configuration of allowed origins that {@link #allowedOrigins} which contains exact
         * origin URLs. It is useful, for example, for Netlify preview builds of the frontend which use a generated
         * subdomain URL.
         */
        private String allowedOriginPatterns;

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public String getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(String allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }
    }

    public static class Schedule {

        private Cron cron = new Cron();

        public Cron getCron() {
            return cron;
        }

        public void setCron(Cron cron) {
            this.cron = cron;
        }

        public static class Cron {

            private Notification notification = new Notification();

            public Notification getNotification() {
                return notification;
            }

            public void setNotification(Notification notification) {
                this.notification = notification;
            }

            public static class Notification {

                /**
                 * CRON expression configuring when to send notifications of changes in comments to admins and
                 * vocabulary authors. Defaults to '-' which disables this functionality.
                 */
                private String comments = "-";

                public String getComments() {
                    return comments;
                }

                public void setComments(String comments) {
                    this.comments = comments;
                }
            }
        }
    }

    public static class Mail {

        /**
         * Human-readable name to use as email sender.
         */
        private String sender;

        public String getSender() {
            return sender;
        }

        public void setSender(String sender) {
            this.sender = sender;
        }
    }

    /**
     * Configuration for initialization of new {@link cz.cvut.kbss.termit.model.acl.AccessControlList}s.
     */
    public static class ACL {

        /**
         * Default access level for users in editor role.
         */
        private AccessLevel defaultEditorAccessLevel = AccessLevel.READ;

        /**
         * Default access level for users in editor role.
         */
        private AccessLevel defaultReaderAccessLevel = AccessLevel.READ;

        public AccessLevel getDefaultEditorAccessLevel() {
            return defaultEditorAccessLevel;
        }

        public void setDefaultEditorAccessLevel(AccessLevel defaultEditorAccessLevel) {
            this.defaultEditorAccessLevel = defaultEditorAccessLevel;
        }

        public AccessLevel getDefaultReaderAccessLevel() {
            return defaultReaderAccessLevel;
        }

        public void setDefaultReaderAccessLevel(AccessLevel defaultReaderAccessLevel) {
            this.defaultReaderAccessLevel = defaultReaderAccessLevel;
        }
    }

    public static class Security {

        public enum ProviderType {
            INTERNAL, OIDC
        }

        /**
         * Determines whether an internal security mechanism or an external OIDC service will be used for
         * authentication.
         * <p>
         * In case na OIDC service is selected, it should be configured using standard Spring Boot OAuth2 properties.
         */
        private ProviderType provider = ProviderType.INTERNAL;

        /**
         * Claim in the authentication token provided by the OIDC service containing roles mapped to TermIt user roles.
         *
         * Supports nested objects via dot notation.
         */
        private String roleClaim = "realm_access.roles";

        public ProviderType getProvider() {
            return provider;
        }

        public void setProvider(ProviderType provider) {
            this.provider = provider;
        }

        public String getRoleClaim() {
            return roleClaim;
        }

        public void setRoleClaim(String roleClaim) {
            this.roleClaim = roleClaim;
        }
    }

    public static class Language {

        /**
         * Path to a file containing definition of the language of types terms can be classified with.
         *
         * The file must be in Turtle format.
         */
        private LanguageSource types = new LanguageSource();

        public LanguageSource getTypes() {
            return types;
        }

        public void setTypes(LanguageSource types) {
            this.types = types;
        }

        public static class LanguageSource {

            private String source;

            public String getSource() {
                return source;
            }

            public void setSource(String source) {
                this.source = source;
            }
        }
    }
}
