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
import cz.cvut.kbss.termit.model.Vocabulary;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Validator;
import java.util.List;
import java.util.Objects;

@Service
public class ResourceRepositoryService extends BaseAssetRepositoryService<Resource>
        implements SupportsLastModification {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceRepositoryService.class);

    private final ResourceDao resourceDao;
    private final TermOccurrenceDao termOccurrenceDao;

    private final IdentifierResolver idResolver;

    private final Configuration.Namespace cfgNamespace;

    @Autowired
    public ResourceRepositoryService(Validator validator, ResourceDao resourceDao,
                                     TermOccurrenceDao termOccurrenceDao,
                                     IdentifierResolver idResolver,
                                     Configuration config) {
        super(validator);
        this.resourceDao = resourceDao;
        this.termOccurrenceDao = termOccurrenceDao;
        this.idResolver = idResolver;
        this.cfgNamespace = config.getNamespace();
    }

    @Override
    protected AssetDao<Resource> getPrimaryDao() {
        return resourceDao;
    }

    @Override
    public List<Resource> findAll() {
        return super.findAll();
    }

    @Override
    public void persist(Resource instance) {
        super.persist(instance);
    }

    @Override
    public Resource update(Resource instance) {
        return super.update(instance);
    }

    @Override
    public void remove(Resource instance) {
        super.remove(instance);
    }

    @Override
    protected void prePersist(Resource instance) {
        super.prePersist(instance);
        if (instance.getUri() == null) {
            instance.setUri(idResolver.generateIdentifier(cfgNamespace.getResource(), instance.getLabel()));
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

    @Override
    protected void preRemove(Resource instance) {
        LOG.trace("Removing term occurrences in resource {} which is about to be removed.", instance);
        termOccurrenceDao.removeAll(instance);
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
}
