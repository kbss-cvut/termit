/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedDomainException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.persistence.dao.DataDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class DataRepositoryService {

    private static final Logger LOG = LoggerFactory.getLogger(DataRepositoryService.class);

    private final DataDao dataDao;

    private final IdentifierResolver idResolver;

    private final Configuration config;

    @Autowired
    public DataRepositoryService(DataDao dataDao, IdentifierResolver idResolver, Configuration config) {
        this.dataDao = dataDao;
        this.idResolver = idResolver;
        this.config = config;
    }

    /**
     * Gets all RDF properties present in the system.
     *
     * @return List of properties, ordered by label
     */
    @Transactional(readOnly = true)
    public List<RdfsResource> findAllProperties() {
        return dataDao.findAllProperties();
    }

    /**
     * Gets all user-defined properties.
     *
     * @return List of custom properties
     */
    @Transactional(readOnly = true)
    public List<CustomAttribute> findAllCustomProperties() {
        return dataDao.findAllCustomAttributes();
    }

    /**
     * Gets basic metadata about a resource with the specified identifier.
     *
     * @param id Resource identifier
     * @return Wrapped matching resource or an empty {@code Optional} if no such resource exists
     */
    public Optional<RdfsResource> find(URI id) {
        return dataDao.find(id);
    }

    /**
     * Persists the specified RDFS resource.
     * <p>
     * This method should be used scarcely or more suitable subclasses of {@link RdfsResource} should be provided as
     * arguments.
     *
     * @param property The resource to persist
     * @see #persistCustomAttribute(CustomAttribute)
     */
    @Transactional
    public void persist(@Nonnull RdfsResource property) {
        LOG.debug("Persisting property {}", property);
        Objects.requireNonNull(property);
        dataDao.persist(property);
    }

    /**
     * Persists the specified custom attribute.
     * <p>
     * Note that this method automatically sets {@link cz.cvut.kbss.jopa.vocabulary.SKOS#CONCEPT} as the attribute
     * domain.
     *
     * @param attribute Attribute to persist
     */
    @Transactional
    public void persistCustomAttribute(@Nonnull CustomAttribute attribute) {
        Objects.requireNonNull(attribute);
        if (attribute.getDomain() == null) {
            attribute.setDomain(URI.create(SKOS.CONCEPT));
        }
        validate(attribute);
        if (attribute.getUri() == null) {
            attribute.setUri(
                    idResolver.generateIdentifier(config.getNamespace().getCustomAttribute(), getLabelForIdentifier(attribute)));
        }
        LOG.debug("Persisting custom attribute {}", attribute);
        dataDao.persist(attribute);
    }

    private String getLabelForIdentifier(CustomAttribute att) {
        if (att.getLabel().contains(config.getPersistence().getLanguage())) {
            return att.getLabel().get(config.getPersistence().getLanguage());
        }
        assert !att.getLabel().isEmpty();
        return att.getLabel().get();
    }

    private static void validate(CustomAttribute attribute) {
        assert attribute.getDomain() != null;
        final String strDomain = attribute.getDomain().toString();
        if (!SKOS.CONCEPT.equals(strDomain) && !Vocabulary.s_c_slovnik.equals(strDomain) && !RDF.STATEMENT.equals(strDomain)) {
            throw new UnsupportedDomainException("Unsupported custom attribute domain: " + attribute.getDomain());
        }

        if (RDF.STATEMENT.equals(strDomain)) {
            if (attribute.getAnnotatedRelationships() == null || attribute.getAnnotatedRelationships().isEmpty()) {
                throw new ValidationException("Custom attribute with rdf:Statement domain must specify applicable properties.");
            }
        }
        if (attribute.getLabel() == null || attribute.getLabel().isEmpty()) {
            throw new ValidationException("Custom attribute must have a label.");
        }
    }

    @Transactional
    public void updateCustomAttribute(@Nonnull CustomAttribute attribute) {
        Objects.requireNonNull(attribute);
        final CustomAttribute existing = dataDao.findCustomAttribute(attribute.getUri())
                                                .orElseThrow(() -> NotFoundException.create(
                                                        CustomAttribute.class, attribute.getUri()));
        existing.setLabel(attribute.getLabel());
        existing.setComment(attribute.getComment());
        existing.setAnnotatedRelationships(attribute.getAnnotatedRelationships());
        LOG.debug("Updating custom attribute {}", existing);
    }

    /**
     * Gets the label of a resource with the specified identifier.
     *
     * @param id       Resource identifier
     * @param language Label language, if null, the vocabulary language is used when available, otherwise the configured
     *                 persistence unit language is used instead.
     * @return Matching resource identifier (if found)
     */
    @Transactional(readOnly = true)
    public Optional<String> getLabel(URI id, @Nullable String language) {
        return dataDao.getLabel(id, language);
    }
}
