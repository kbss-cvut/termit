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
package cz.cvut.kbss.termit.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.config.WebAppConfig;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.AuthenticationToken;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class Environment {

    public static final String BASE_URI = Vocabulary.ONTOLOGY_IRI_termit;

    public static final String LANGUAGE = "en";

    private static UserAccount currentUser;

    private static ObjectMapper objectMapper;

    private static ObjectMapper jsonLdObjectMapper;

    /**
     * Initializes security context with the specified user.
     *
     * @param user User to set as currently authenticated
     */
    public static void setCurrentUser(UserAccount user) {
        currentUser = user;
        final TermItUserDetails userDetails = new TermItUserDetails(user, new HashSet<>());
        SecurityContext context = new SecurityContextImpl();
        context.setAuthentication(new AuthenticationToken(userDetails.getAuthorities(), userDetails));
        SecurityContextHolder.setContext(context);
    }

    /**
     * @see #setCurrentUser(UserAccount)
     */
    public static void setCurrentUser(User user) {
        final UserAccount ua = new UserAccount();
        ua.setUri(user.getUri());
        ua.setFirstName(user.getFirstName());
        ua.setLastName(user.getLastName());
        ua.setUsername(user.getUsername());
        ua.setTypes(user.getTypes());
        setCurrentUser(ua);
    }

    public static UserAccount getCurrentUser() {
        return currentUser;
    }

    /**
     * Resets security context, removing any previously set data.
     */
    public static void resetCurrentUser() {
        currentUser = null;
        SecurityContextHolder.clearContext();
    }

    /**
     * Gets a Jackson {@link ObjectMapper} for mapping JSON to Java and vice versa.
     *
     * @return {@code ObjectMapper}
     */
    public static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = WebAppConfig.createJsonObjectMapper();
        }
        return objectMapper;
    }

    /**
     * Gets a Jackson {@link ObjectMapper} for mapping JSON-LD to Java and vice versa.
     *
     * @return {@code ObjectMapper}
     */
    public static ObjectMapper getJsonLdObjectMapper() {
        if (jsonLdObjectMapper == null) {
            jsonLdObjectMapper = WebAppConfig.createJsonLdObjectMapper();
        }
        return jsonLdObjectMapper;
    }

    /**
     * Creates a Jackson JSON-LD message converter.
     *
     * @return JSON-LD message converter
     */
    public static HttpMessageConverter<?> createJsonLdMessageConverter() {
        final MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(
                getJsonLdObjectMapper());
        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.valueOf(JsonLd.MEDIA_TYPE)));
        return converter;
    }

    public static HttpMessageConverter<?> createDefaultMessageConverter() {
        return new MappingJackson2HttpMessageConverter(getObjectMapper());
    }

    public static HttpMessageConverter<?> createStringEncodingMessageConverter() {
        return new StringHttpMessageConverter(StandardCharsets.UTF_8);
    }

    public static HttpMessageConverter<?> createResourceMessageConverter() {
        return new ResourceHttpMessageConverter();
    }

    public static InputStream loadFile(String file) {
        return Environment.class.getClassLoader().getResourceAsStream(file);
    }

    /**
     * Loads TermIt ontological model into the underlying repository, so that RDFS inference (mainly class and property
     * hierarchy) can be exploited.
     * <p>
     * Note that the specified {@code em} has to be transactional, so that a connection to the underlying repository is
     * open.
     *
     * @param em Transactional {@code EntityManager} used to unwrap the underlying repository
     */
    public static void addModelStructureForRdfsInference(EntityManager em) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            conn.begin();
            conn.add(Environment.class.getClassLoader().getResourceAsStream("ontologies/popis-dat-model.ttl"), BASE_URI,
                    RDFFormat.TURTLE);
            conn.add(new File("ontology/termit-model.ttl"), BASE_URI, RDFFormat.TURTLE);
            conn.add(new File("rulesets/rules-termit-spin.ttl"), BASE_URI, RDFFormat.TURTLE);
            conn.add(Environment.class.getClassLoader().getResourceAsStream("ontologies/skos.rdf"), "", RDFFormat.RDFXML);
            conn.commit();
        } catch (IOException e) {
            throw new RuntimeException("Unable to load TermIt model for import.", e);
        }
    }

    public static List<TermDto> termsToDtos(List<Term> terms) {
        return terms.stream().map(TermDto::new).collect(Collectors.toList());
    }

    public static cz.cvut.kbss.termit.model.Vocabulary cloneVocabulary(cz.cvut.kbss.termit.model.Vocabulary original) {
        final cz.cvut.kbss.termit.model.Vocabulary clone = Generator.generateVocabulary();
        clone.setUri(original.getUri());
        clone.setLabel(original.getLabel());
        clone.getGlossary().setUri(original.getGlossary().getUri());
        clone.getModel().setUri(original.getModel().getUri());
        return clone;
    }

    public static void insertContextBasedOnCanonical(URI workingCtx, URI canonicalCtx, EntityManager em) {
        em.createNativeQuery("INSERT DATA { GRAPH ?g { ?g ?basedOn ?canonical . }}")
          .setParameter("g", workingCtx)
          .setParameter("basedOn", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_vychazi_z_verze))
          .setParameter("canonical", canonicalCtx)
          .executeUpdate();
    }
}
