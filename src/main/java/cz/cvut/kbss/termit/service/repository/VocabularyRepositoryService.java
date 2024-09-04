/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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

import cz.cvut.kbss.termit.dto.AggregatedChangeInfo;
import cz.cvut.kbss.termit.dto.PrefixDeclaration;
import cz.cvut.kbss.termit.dto.RdfsStatement;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.exception.AssetRemovalException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.importing.VocabularyImportException;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Model;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.dao.BaseAssetDao;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.MessageFormatter;
import cz.cvut.kbss.termit.service.importer.VocabularyImporter;
import cz.cvut.kbss.termit.service.importer.VocabularyImporters;
import cz.cvut.kbss.termit.service.snapshot.SnapshotProvider;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import jakarta.validation.Validator;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CacheConfig(cacheNames = "vocabularies")
@Service
public class VocabularyRepositoryService extends BaseAssetRepositoryService<Vocabulary, VocabularyDto> {

    private final IdentifierResolver idResolver;

    private final VocabularyDao vocabularyDao;

    private final EditableVocabularies editableVocabularies;

    private final Configuration config;

    private final VocabularyImporters importers;

    private final DtoMapper dtoMapper;

    @Autowired
    public VocabularyRepositoryService(VocabularyDao vocabularyDao, IdentifierResolver idResolver,
                                       Validator validator, EditableVocabularies editableVocabularies,
                                       Configuration config, VocabularyImporters importers, DtoMapper dtoMapper) {
        super(validator);
        this.vocabularyDao = vocabularyDao;
        this.idResolver = idResolver;
        this.editableVocabularies = editableVocabularies;
        this.config = config;
        this.importers = importers;
        this.dtoMapper = dtoMapper;
    }

    @Override
    protected BaseAssetDao<Vocabulary> getPrimaryDao() {
        return vocabularyDao;
    }

    // Cache only if all vocabularies are editable
    @Cacheable(condition = "@'termit-cz.cvut.kbss.termit.util.Configuration'.workspace.allVocabulariesEditable")
    @Override
    public List<VocabularyDto> findAll() {
        return super.findAll();
    }

    @Override
    protected Vocabulary postLoad(@NotNull Vocabulary instance) {
        super.postLoad(instance);
        if (!config.getWorkspace().isAllVocabulariesEditable() && !editableVocabularies.isEditable(instance)) {
            instance.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_pouze_pro_cteni);
        }
        return instance;
    }

    @Override
    protected VocabularyDto mapToDto(Vocabulary entity) {
        return dtoMapper.vocabularyToVocabularyDto(entity);
    }

    @CacheEvict(allEntries = true)
    @Override
    @Transactional
    public void persist(@NotNull Vocabulary instance) {
        super.persist(instance);
    }

    @Override
    protected void prePersist(@NotNull Vocabulary instance) {
        super.prePersist(instance);
        if (instance.getUri() == null) {
            instance.setUri(
                    idResolver.generateIdentifier(config.getNamespace().getVocabulary(), instance.getPrimaryLabel()));
        }
        verifyIdentifierUnique(instance);
        initGlossaryAndModel(instance);
        initDocument(instance);
        if (instance.getDocument() != null) {
            instance.getDocument().setVocabulary(null);
        }
    }

    private void initGlossaryAndModel(Vocabulary vocabulary) {
        final String iriBase = vocabulary.getUri().toString();
        if (vocabulary.getGlossary() == null) {
            vocabulary.setGlossary(new Glossary());
            vocabulary.getGlossary().setUri(idResolver.generateIdentifier(iriBase, config.getGlossary().getFragment()));
        }
        if (vocabulary.getModel() == null) {
            vocabulary.setModel(new Model());
            vocabulary.getModel().setUri(idResolver.generateIdentifier(iriBase, Constants.DEFAULT_MODEL_IRI_COMPONENT));
        }
    }

    private void initDocument(Vocabulary vocabulary) {
        if (vocabulary.getDocument() != null) {
            return;
        }
        final Document doc = new Document();
        doc.setUri(idResolver.generateIdentifier(vocabulary.getUri().toString(),
                                                 Constants.DEFAULT_DOCUMENT_IRI_COMPONENT));
        doc.setLabel(
                new MessageFormatter(config.getPersistence().getLanguage()).formatMessage("vocabulary.document.label",
                                                                                          vocabulary.getPrimaryLabel()));
        vocabulary.setDocument(doc);
    }

    @Override
    protected void preUpdate(@NotNull Vocabulary instance) {
        super.preUpdate(instance);
        final Vocabulary original = findRequired(instance.getUri());
        verifyVocabularyImports(instance, original);
        // ACL reference does not change, but it can be missing in case the instance arrived from client
        instance.setAcl(original.getAcl());
        SnapshotProvider.verifySnapshotNotModified(original);
    }

    /**
     * Ensures that possible vocabulary import removals are not prevented by existing inter-vocabulary term
     * relationships (terms from the updated vocabulary having parents from vocabularies whose import has been
     * removed).
     */
    private void verifyVocabularyImports(Vocabulary update, Vocabulary original) {
        final Set<URI> removedImports = new HashSet<>(Utils.emptyIfNull(original.getImportedVocabularies()));
        removedImports.removeAll(Utils.emptyIfNull(update.getImportedVocabularies()));
        final Set<URI> invalid = removedImports.stream().filter(ri -> vocabularyDao
                .hasInterVocabularyTermRelationships(update.getUri(), ri)).collect(
                Collectors.toSet());
        if (!invalid.isEmpty()) {
            throw new VocabularyImportException("Cannot remove imports of vocabularies " + invalid +
                                                        ", there are still relationships between terms.",
                                                "error.vocabulary.update.imports.danglingTermReferences");
        }
    }

    @PreAuthorize("@vocabularyAuthorizationService.canModify(#instance)")
    @CacheEvict(allEntries = true)
    @Override
    @Transactional
    public Vocabulary update(Vocabulary instance) {
        return super.update(instance);
    }

    public Collection<URI> getTransitivelyImportedVocabularies(Vocabulary entity) {
        return vocabularyDao.getTransitivelyImportedVocabularies(entity.getUri());
    }

    public Set<URI> getRelatedVocabularies(Vocabulary entity) {
        return vocabularyDao.getRelatedVocabularies(entity, Constants.SKOS_CONCEPT_MATCH_RELATIONSHIPS);
    }

    @Transactional(readOnly = true)
    public List<AggregatedChangeInfo> getChangesOfContent(Vocabulary vocabulary) {
        return vocabularyDao.getChangesOfContent(vocabulary);
    }

    @CacheEvict(allEntries = true)
    @Transactional
    public Vocabulary importVocabulary(boolean rename, MultipartFile file) {
        Objects.requireNonNull(file);
        try {
            String contentType = resolveContentType(file);
            return importers.importVocabulary(
                    new VocabularyImporter.ImportConfiguration(rename, null, this::initDocument),
                    new VocabularyImporter.ImportInput(contentType, file.getInputStream()));
        } catch (VocabularyImportException e) {
            throw e;
        } catch (Exception e) {
            throw new VocabularyImportException("Unable to import vocabulary, because of: " + e.getMessage());
        }
    }

    private static String resolveContentType(MultipartFile file) throws IOException {
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, file.getName());
        metadata.add(Metadata.CONTENT_TYPE, file.getContentType());
        return new Tika().detect(file.getInputStream(), metadata);
    }

    @CacheEvict(allEntries = true)
    @Transactional
    public Vocabulary importVocabulary(URI vocabularyIri, MultipartFile file) {
        Objects.requireNonNull(file);
        try {
            String contentType = resolveContentType(file);
            return importers.importVocabulary(
                    new VocabularyImporter.ImportConfiguration(false, vocabularyIri, this::initDocument),
                    new VocabularyImporter.ImportInput(contentType, file.getInputStream()));
        } catch (VocabularyImportException e) {
            throw e;
        } catch (Exception e) {
            throw new VocabularyImportException("Unable to import vocabulary, because of: " + e.getMessage(), e);
        }
    }

    public long getLastModified() {
        return vocabularyDao.getLastModified();
    }

    /**
     * Removes a vocabulary unless:
     * <ul>
     *     <li>it is imported by another vocabulary, other relation with another vocabulary exists or</li>
     *     <li>it contains terms that are a part of relations with another vocabulary</li>
     * </ul>
     */
    @PreAuthorize("@vocabularyAuthorizationService.canRemove(#instance)")
    @CacheEvict(allEntries = true)
    @Transactional
    @Override
    public void remove(Vocabulary instance) {
        super.remove(instance);
    }

    /**
     * Ensures that the vocabulary to be removed complies with the rules allowing removal.
     * <ul>
     *     <li>it is imported by another vocabulary or</li>
     *     <li>it contains terms that are a part of relations with another vocabulary</li>
     * </ul>
     *
     * @param instance The instance to be removed, not {@code null}
     */
    @Override
    protected void preRemove(@NotNull Vocabulary instance) {
        ensureNotImported(instance);
        ensureNoTermRelationsExists(instance);
        super.preRemove(instance);
    }

    /**
     * Ensures there is no other vocabulary importing the {@code vocabulary}
     *
     * @param vocabulary The Vocabulary to search if its imported
     * @throws AssetRemovalException when there is a vocabulary importing the {@code vocabulary}
     */
    private void ensureNotImported(Vocabulary vocabulary) throws AssetRemovalException {
        final List<Vocabulary> vocabularies = vocabularyDao.getImportingVocabularies(vocabulary);
        if (!vocabularies.isEmpty()) {
            throw new AssetRemovalException(
                    "Vocabulary cannot be removed. It is referenced from other vocabularies: "
                            + vocabularies.stream().map(Vocabulary::getPrimaryLabel).collect(Collectors.joining(", ")));
        }
    }

    /**
     * Ensures there are no terms in other vocabularies with a relation to any term in this {@code vocabulary}
     *
     * @param vocabulary The vocabulary
     * @throws AssetRemovalException when there is a vocabulary with a term and relation to a term in the {@code vocabulary}
     */
    private void ensureNoTermRelationsExists(Vocabulary vocabulary) throws AssetRemovalException {
        final List<RdfsStatement> relations = vocabularyDao.getTermRelations(vocabulary);
        if (!relations.isEmpty()) {
            throw new AssetRemovalException("Vocabulary cannot be removed. There are relations with other vocabularies.");
        }
    }

    public Optional<List<ValidationResult>> validateContents(URI vocabulary) {
        return vocabularyDao.validateContents(vocabulary).getCachedResult();
    }

    public Integer getTermCount(Vocabulary vocabulary) {
        return vocabularyDao.getTermCount(vocabulary);
    }

    public List<RdfsStatement> getTermRelations(Vocabulary vocabulary) {
        return vocabularyDao.getTermRelations(vocabulary);
    }

    public List<RdfsStatement> getVocabularyRelations(Vocabulary vocabulary, Collection<URI> excludedRelations) {
        return vocabularyDao.getVocabularyRelations(vocabulary, excludedRelations);
    }


    @Transactional(readOnly = true)
    public List<Snapshot> findSnapshots(Vocabulary vocabulary) {
        return vocabularyDao.findSnapshots(vocabulary);
    }

    @Transactional(readOnly = true)
    public Vocabulary findVersionValidAt(Vocabulary vocabulary, Instant at) {
        return vocabularyDao.findVersionValidAt(vocabulary, at)
                            .orElseThrow(() -> new NotFoundException("No version valid at " + at + " exists."));
    }

    /**
     * Resolves preferred prefix of a vocabulary with the specified identifier.
     *
     * @param vocabularyUri Vocabulary identifier
     * @return Prefix declaration, possibly empty
     */
    @Transactional(readOnly = true)
    public PrefixDeclaration resolvePrefix(URI vocabularyUri) {
        return vocabularyDao.resolvePrefix(vocabularyUri);
    }
}
