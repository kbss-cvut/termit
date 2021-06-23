/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If
 * not, see <https://www.gnu.org/licenses/>.
 */

package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.asset.provenance.SupportsLastModification;
import cz.cvut.kbss.termit.dto.assignment.ResourceTermAssignments;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermAssignment;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.persistence.dao.AssetDao;
import cz.cvut.kbss.termit.persistence.dao.ResourceDao;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Validator;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@CacheConfig(cacheNames = "resources")
@Service
public class ResourceRepositoryService extends BaseAssetRepositoryService<Resource>
    implements SupportsLastModification {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceRepositoryService.class);

    private final ResourceDao resourceDao;
    private final TermOccurrenceDao termOccurrenceDao;

    private final TermAssignmentRepositoryService assignmentService;

    private final IdentifierResolver idResolver;

    private final Configuration.Namespace cfgNamespace;

    @Autowired
    public ResourceRepositoryService(Validator validator, ResourceDao resourceDao,
                                     TermOccurrenceDao termOccurrenceDao,
                                     TermAssignmentRepositoryService assignmentService,
                                     IdentifierResolver idResolver,
                                     Configuration config) {
        super(validator);
        this.resourceDao = resourceDao;
        this.termOccurrenceDao = termOccurrenceDao;
        this.assignmentService = assignmentService;
        this.idResolver = idResolver;
        this.cfgNamespace = config.getNamespace();
    }

    @Override
    protected AssetDao<Resource> getPrimaryDao() {
        return resourceDao;
    }

    @Cacheable
    @Override
    public List<Resource> findAll() {
        return super.findAll();
    }

    @CacheEvict(allEntries = true)
    @Override
    public void persist(Resource instance) {
        super.persist(instance);
    }

    @CacheEvict(allEntries = true)
    @Override
    public Resource update(Resource instance) {
        return super.update(instance);
    }

    @CacheEvict(allEntries = true)
    @Override
    public void remove(Resource instance) {
        super.remove(instance);
    }

    @Override
    protected void prePersist(Resource instance) {
        super.prePersist(instance);
        if (instance.getUri() == null) {
            instance.setUri(
                idResolver.generateIdentifier(cfgNamespace.getResource(), instance.getLabel()));
        }
        verifyIdentifierUnique(instance);
    }

    /**
     * Persists the specified Resource in the context of the specified Vocabulary.
     *
     * @param resource   Resource to persist
     * @param vocabulary Vocabulary context
     * @throws IllegalArgumentException If the specified Resource is neither a {@code Document}
     *                                  nor a {@code File}
     */
    @Transactional
    public void persist(Resource resource, Vocabulary vocabulary) {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(vocabulary);
        prePersist(resource);
        resourceDao.persist(resource, vocabulary);
    }

    /**
     * Gets terms the specified resource is annotated with.
     *
     * @param resource Annotated resource
     * @return List of terms annotating the specified resource
     */
    public List<Term> findTags(Resource resource) {
        return resourceDao.findTerms(resource);
    }

    /**
     * Gets term assignments related to the specified resource.
     * <p>
     * This includes both assignments and occurrences.
     *
     * @param resource Target resource
     * @return List of term assignments and occurrences
     */
    public List<TermAssignment> findAssignments(Resource resource) {
        return assignmentService.findAll(resource);
    }

    /**
     * Gets aggregated information about Terms assigned to the specified Resource.
     * <p>
     * Since retrieving all the assignments and occurrences related to the specified Resource may
     * be time consuming and
     * is rarely required, this method provides aggregate information in that the returned
     * instances contain only
     * distinct Terms assigned to/occurring in a Resource together with information about how
     * many times they occur and
     * whether they are suggested or asserted.
     *
     * @param resource Resource to get assignment info for
     * @return Aggregated assignment information for Resource
     */
    public List<ResourceTermAssignments> getAssignmentInfo(Resource resource) {
        return assignmentService.getResourceAssignmentInfo(resource);
    }

    /**
     * Annotates a resource with vocabulary terms.
     *
     * @param resource Resource to be annotated
     * @param iTerms   Terms to be used for annotation
     */
    @Transactional
    public void setTags(Resource resource, final Collection<URI> iTerms) {
        assignmentService.setOnResource(resource, iTerms);
    }

    @Override
    protected void preRemove(Resource instance) {
        LOG.trace("Removing term occurrences in resource {} which is about to be removed.",
            instance);
        termOccurrenceDao.removeAll(instance);
        assignmentService.removeAll(instance);
        removeFromParentDocumentIfFile(instance);
    }

    private void removeFromParentDocumentIfFile(Resource instance) {
        if (!(instance instanceof File)) {
            return;
        }
        final File file = (File) instance;
        final Document parent = file.getDocument();
        if (parent != null) {
            LOG.trace("Removing file {} from its parent document {}.", instance, parent);
            // Need to detach the parent because we may want to merge it into a vocabulary context,
            // which would cause issues because it was originally loaded from the default context
            resourceDao.detach(parent);
            parent.removeFile(file);
            resourceDao.update(parent);
        }
    }

    @Override
    public long getLastModified() {
        return resourceDao.getLastModified();
    }

    /**
     * Moves the triples of the document from the original vocabulary to the default context and
     * from the
     * new vocabulary to the context of this vocabulary.
     *
     * @param vOriginal original version of the vocabulary (before update)
     * @param vNew      new version of the vocabulary (after update)
     */
    public void rewireDocumentsOnVocabularyUpdate(final Vocabulary vOriginal,
                                                  final Vocabulary vNew) {
        final Document dOriginal = vOriginal.getDocument();
        final Document dNew = vNew.getDocument();

        if (Objects.equals(dOriginal, dNew)) {
            return;
        }

        if (dOriginal != null) {
            // rewire to default ctx
            resourceDao.updateDocumentForVocabulary(dOriginal, dOriginal, null);
        }

        if (dNew != null) {
            final Document dNewManaged = (Document) getRequiredReference(dNew.getUri());
            // rewire to vocabulary ctx
            resourceDao.updateDocumentForVocabulary(dNewManaged, dNew, vNew.getUri());
        }
    }
}
