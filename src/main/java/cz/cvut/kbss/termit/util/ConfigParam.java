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

/**
 * Application configuration parameters, loaded from {@code config.properties} provided on classpath.
 */
public enum ConfigParam {

    /**
     * URL of the main application repository.
     */
    REPOSITORY_URL("repository.url"),

    /**
     * Public URL of the main application repository.
     */
    REPOSITORY_PUBLIC_URL("repository.publicUrl"),

    /**
     * OntoDriver class for the repository.
     */
    DRIVER("persistence.driver"),

    /**
     * Language used to store strings in the repository (persistence unit language).
     */
    LANGUAGE("persistence.language"),

    /**
     * Username for connecting to the application repository.
     */
    REPO_USERNAME("repository.username"),

    /**
     * Password for connecting to the application repository.
     */
    REPO_PASSWORD("repository.password"),

    /**
     * Secret key used when hashing a JWT.
     */
    JWT_SECRET_KEY("jwt.secretKey"),

    /**
     * Namespace for vocabulary identifiers.
     */
    NAMESPACE_VOCABULARY("namespace.vocabulary"),

    /**
     * Namespace for user identifiers.
     */
    NAMESPACE_USER("namespace.user"),

    /**
     * Namespace for resource identifiers.
     */
    NAMESPACE_RESOURCE("namespace.resource"),

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
     * <p>
     * Defaults to {@link Constants#DEFAULT_TERM_NAMESPACE_SEPARATOR}.
     */
    TERM_NAMESPACE_SEPARATOR("namespace.term.separator"),

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
     * <p>
     * Defaults to {@link Constants#DEFAULT_FILE_NAMESPACE_SEPARATOR}.
     */
    FILE_NAMESPACE_SEPARATOR("namespace.file.separator"),

    /**
     * URL of the text analysis service.
     */
    TEXT_ANALYSIS_SERVICE_URL("textAnalysis.url"),

    /**
     * Specifies folder in which admin credentials are saved when his account is generated.
     *
     * @see #ADMIN_CREDENTIALS_FILE
     */
    ADMIN_CREDENTIALS_LOCATION("admin.credentialsLocation"),

    /**
     * Name of the file in which admin credentials are saved when his account is generated.
     * <p>
     * This file is stored in the {@link #ADMIN_CREDENTIALS_LOCATION}.
     *
     * @see #ADMIN_CREDENTIALS_LOCATION
     */
    ADMIN_CREDENTIALS_FILE("admin.credentialsFile"),

    /**
     * Specifies root directory in which document files are stored.
     */
    FILE_STORAGE("file.storage"),

    /**
     * Extension appended to asset identifier (presumably a vocabulary ID) to denote its change tracking context
     * identifier.
     */
    CHANGE_TRACKING_CONTEXT_EXTENSION("changetracking.context.extension"),

    /**
     * IRI of the repository context used to store comments (discussion to assets)
     */
    COMMENTS_CONTEXT("comments.context");

    private final String parameter;

    ConfigParam(String parameter) {
        this.parameter = parameter;
    }

    @Override
    public String toString() {
        return parameter;
    }
}
