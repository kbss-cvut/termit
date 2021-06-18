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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;

import javax.validation.constraints.NotNull;
import java.util.Optional;

/**
 * Represents application-wide configuration.
 * <p>
 * The runtime configuration consists of predefined default values and configuration loaded from config files on
 * classpath. Values from config files supersede the default values.
 */
@org.springframework.context.annotation.Configuration
@ConfigurationProperties("termit")
@Primary
public class Configuration {
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

    @org.springframework.context.annotation.Configuration
    @ConfigurationProperties(prefix = "persistence")
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

    @org.springframework.context.annotation.Configuration
    @ConfigurationProperties(prefix = "repository")
    public static class Repository {
        /**
         * URL of the main application repository.
         */
        @NotNull
        String url;
        /**
         * Public URL of the main application repository.
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

    @org.springframework.context.annotation.Configuration
    @ConfigurationProperties(prefix = "changetracking")
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

    @org.springframework.context.annotation.Configuration
    @ConfigurationProperties(prefix = "comments")
    public static class Comments {
        /**
         * IRI of the repository context used to store comments (discussion to assets)
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

    @org.springframework.context.annotation.Configuration
    @ConfigurationProperties(prefix = "namespace")
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
         * Since Term identifier is given by the identifier of the Vocabulary it belongs to and its own normalized label,
         * this separator is used to (optionally) configure the Term identifier namespace.
         * <p>
         * For example, if we have a Vocabulary with IRI {@code http://www.example.org/ontologies/vocabularies/metropolitan-plan}
         * and a Term with normalized label {@code inhabited-area}, the resulting IRI will be {@code
         * http://www.example.org/ontologies/vocabularies/metropolitan-plan/SEPARATOR/inhabited-area}, where 'SEPARATOR' is
         * the value of this configuration parameter.
         */
        private NamespaceDetail term = new NamespaceDetail();
        /**
         * Separator of File namespace from the parent Document identifier.
         * <p>
         * Since File identifier is given by the identifier of the Document it belongs to and its own normalized label,
         * this separator is used to (optionally) configure the File identifier namespace.
         * <p>
         * For example, if we have a Document with IRI {@code http://www.example.org/ontologies/resources/metropolitan-plan}
         * and a File with normalized label {@code main-file}, the resulting IRI will be {@code
         * http://www.example.org/ontologies/resources/metropolitan-plan/SEPARATOR/main-file}, where 'SEPARATOR' is
         * the value of this configuration parameter.
         */
        private NamespaceDetail file = new NamespaceDetail();

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

    @org.springframework.context.annotation.Configuration
    @ConfigurationProperties(prefix = "admin")
    public static class Admin {
        /**
         * Specifies folder in which admin credentials are saved when his account is generated.
         */
        @NotNull
        String credentialsLocation;
        /**
         * Name of the file in which admin credentials are saved when his account is generated.
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

    @org.springframework.context.annotation.Configuration
    @ConfigurationProperties(prefix = "file")
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

    @org.springframework.context.annotation.Configuration
    @ConfigurationProperties(prefix = "jwt")
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

    @org.springframework.context.annotation.Configuration
    @ConfigurationProperties(prefix = "textanalysis")
    public static class TextAnalysis {
        /**
         * URL of the text analysis service.
         */
        @NotNull
        String url;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    @org.springframework.context.annotation.Configuration
    @ConfigurationProperties(prefix = "glossary")
    public static class Glossary {
        /**
         * URL of the text analysis service.
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
}
