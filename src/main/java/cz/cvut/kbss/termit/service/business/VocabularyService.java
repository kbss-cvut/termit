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
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.asset.provenance.SupportsLastModification;
import cz.cvut.kbss.termit.dto.AggregatedChangeInfo;
import cz.cvut.kbss.termit.dto.RdfsStatement;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.acl.AccessControlListDto;
import cz.cvut.kbss.termit.dto.filter.ChangeRecordFilterDto;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.event.VocabularyContentModifiedEvent;
import cz.cvut.kbss.termit.event.VocabularyCreatedEvent;
import cz.cvut.kbss.termit.event.VocabularyEvent;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.persistence.snapshot.SnapshotCreator;
import cz.cvut.kbss.termit.service.changetracking.ChangeRecordProvider;
import cz.cvut.kbss.termit.service.export.ExportFormat;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.service.security.authorization.VocabularyAuthorizationService;
import cz.cvut.kbss.termit.service.snapshot.SnapshotProvider;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.TypeAwareClasspathResource;
import cz.cvut.kbss.termit.util.TypeAwareFileSystemResource;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import cz.cvut.kbss.termit.util.throttle.Throttle;
import cz.cvut.kbss.termit.util.throttle.ThrottledFuture;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static cz.cvut.kbss.termit.util.Constants.VOCABULARY_REMOVAL_IGNORED_RELATIONS;

/**
 * Business logic concerning vocabularies.
 * <p>
 * Note that retrieval methods that take an instance of {@link Vocabulary} as argument do not have explicit
 * authorization annotations. It is assumed that read access has already been authorized when the {@link Vocabulary}
 * instance/reference was retrieved previously.
 */
@Service
public class VocabularyService
        implements CrudService<Vocabulary, VocabularyDto>, ChangeRecordProvider<Vocabulary>, SupportsLastModification, ApplicationEventPublisherAware {

    private static final Logger LOG = LoggerFactory.getLogger(VocabularyService.class);

    private final VocabularyRepositoryService repositoryService;

    private final ChangeRecordService changeRecordService;

    private final TermService termService;

    private final VocabularyContextMapper contextMapper;

    private final AccessControlListService aclService;

    private final VocabularyAuthorizationService authorizationService;

    private final ApplicationContext context;

    private ApplicationEventPublisher eventPublisher;

    public VocabularyService(VocabularyRepositoryService repositoryService,
                             ChangeRecordService changeRecordService,
                             @Lazy TermService termService,
                             VocabularyContextMapper contextMapper,
                             AccessControlListService aclService,
                             VocabularyAuthorizationService authorizationService,
                             ApplicationContext context) {
        this.repositoryService = repositoryService;
        this.changeRecordService = changeRecordService;
        this.termService = termService;
        this.contextMapper = contextMapper;
        this.aclService = aclService;
        this.authorizationService = authorizationService;
        this.context = context;
    }

    /**
     * Receives {@link VocabularyContentModifiedEvent} and triggers validation. The goal for this is to get the results
     * cached and do not force users to wait for validation when they request it.
     */
    @EventListener({VocabularyContentModifiedEvent.class, VocabularyCreatedEvent.class})
    public void onVocabularyContentModified(VocabularyEvent event) {
        repositoryService.validateContents(event.getVocabularyIri());
    }

    @Override
    @PostFilter("@vocabularyAuthorizationService.canRead(filterObject)")
    public List<VocabularyDto> findAll() {
        // PostFilter modifies the data in-place, which caused unwanted updates to the cache (since we are caching
        // the whole list in VocabularyRepositoryService)
        return new ArrayList<>(repositoryService.findAll());
    }

    @Override
    public long getLastModified() {
        return repositoryService.getLastModified();
    }

    /**
     * @return {@link cz.cvut.kbss.termit.dto.VocabularyDto}
     */
    @Override
    @PostAuthorize("@vocabularyAuthorizationService.canRead(returnObject)")
    public Optional<Vocabulary> find(URI id) {
        return repositoryService.find(id).map(v -> {
            // Enhance vocabulary data with info on current user's access level
            final cz.cvut.kbss.termit.dto.VocabularyDto dto = new cz.cvut.kbss.termit.dto.VocabularyDto(v);
            dto.setAccessLevel(getAccessLevel(v));
            return dto;
        });
    }

    @Override
    @PostAuthorize("@vocabularyAuthorizationService.canRead(returnObject)")
    public Vocabulary findRequired(URI id) {
        // Enhance vocabulary data with info on current user's access level
        final cz.cvut.kbss.termit.dto.VocabularyDto dto = new cz.cvut.kbss.termit.dto.VocabularyDto(
                repositoryService.findRequired(id));
        dto.setAccessLevel(getAccessLevel(dto));
        return dto;
    }

    @Override
    @PostAuthorize("@vocabularyAuthorizationService.canRead(returnObject)")
    public Vocabulary getReference(URI id) {
        return repositoryService.getReference(id);
    }

    @Override
    @Transactional
    @PreAuthorize("@vocabularyAuthorizationService.canCreate()")
    public void persist(Vocabulary instance) {
        repositoryService.persist(instance);
        final AccessControlList acl = aclService.createFor(instance);
        instance.setAcl(acl.getUri());
        eventPublisher.publishEvent(new VocabularyCreatedEvent(this, instance.getUri()));
    }

    @Override
    @PreAuthorize("@vocabularyAuthorizationService.canModify(#instance)")
    public Vocabulary update(Vocabulary instance) {
        return repositoryService.update(instance);
    }

    /**
     * Gets identifiers of all vocabularies imported by the specified vocabulary, including transitively imported ones.
     *
     * @param entity Base vocabulary, whose imports should be retrieved
     * @return Collection of (transitively) imported vocabularies
     */
    @PreAuthorize("@vocabularyAuthorizationService.canRead(#entity)")
    public Collection<URI> getTransitivelyImportedVocabularies(Vocabulary entity) {
        return repositoryService.getTransitivelyImportedVocabularies(entity);
    }

    /**
     * Gets identifiers of all vocabularies whose terms are in a SKOS relationship with the specified vocabulary or are
     * explicitly imported by it.
     * <p>
     * This includes transitively related.
     *
     * @param entity Base vocabulary whose related vocabularies to return
     * @return Set of vocabulary identifiers
     */
    @PreAuthorize("@vocabularyAuthorizationService.canRead(#entity)")
    public Set<URI> getRelatedVocabularies(Vocabulary entity) {
        return repositoryService.getRelatedVocabularies(entity);
    }

    /**
     * Gets statements representing SKOS relationships between terms from the specified vocabulary and terms from other
     * vocabularies.
     *
     * @param vocabulary Vocabulary whose terms' relationships to retrieve
     * @return List of RDF statements
     */
    @PreAuthorize("@vocabularyAuthorizationService.canRead(#vocabulary)")
    public List<RdfsStatement> getTermRelations(Vocabulary vocabulary) {
        return repositoryService.getTermRelations(vocabulary);
    }

    /**
     * Gets statements representing relationships between the specified vocabulary and other vocabularies.
     * <p>
     * A selected set of relationships is excluded (for example, versioning relationships).
     *
     * @param vocabulary Vocabulary whose relationships to retrieve
     * @return List of RDF statements
     */
    @PreAuthorize("@vocabularyAuthorizationService.canRead(#vocabulary)")
    public List<RdfsStatement> getVocabularyRelations(Vocabulary vocabulary) {
        return repositoryService.getVocabularyRelations(vocabulary, VOCABULARY_REMOVAL_IGNORED_RELATIONS);
    }

    /**
     * Imports a new vocabulary from the specified file.
     * <p>
     * The file could be a text file containing RDF.
     *
     * @param rename true, if the IRIs should be modified in order to prevent clashes with existing data
     * @param file   File from which to import the vocabulary
     * @return The imported vocabulary metadata
     * @throws cz.cvut.kbss.termit.exception.importing.VocabularyImportException If the import fails
     * @throws cz.cvut.kbss.termit.exception.importing.VocabularyExistsException If a vocabulary with a glossary
     *                                                                           matching the one in the imported data
     *                                                                           already exists
     */
    @Transactional
    @PreAuthorize("@vocabularyAuthorizationService.canCreate()")
    public Vocabulary importVocabulary(boolean rename, MultipartFile file) {
        final Vocabulary imported = repositoryService.importVocabulary(rename, file);
        final AccessControlList acl = aclService.createFor(imported);
        imported.setAcl(acl.getUri());
        eventPublisher.publishEvent(new VocabularyCreatedEvent(this, imported.getUri()));
        return imported;
    }

    /**
     * Imports a vocabulary from the specified file.
     * <p>
     * The file could be a text file containing RDF. If a vocabulary with the specified identifier already exists, its
     * content is overridden by the input data.
     *
     * @param vocabularyIri IRI of the vocabulary to be created
     * @param file          File from which to import the vocabulary
     * @return The imported vocabulary metadata
     * @throws cz.cvut.kbss.termit.exception.importing.VocabularyImportException If the import fails
     */
    @PreAuthorize("@vocabularyAuthorizationService.canReimport(#vocabularyIri)")
    public Vocabulary importVocabulary(URI vocabularyIri, MultipartFile file) {
        return repositoryService.importVocabulary(vocabularyIri, file);
    }

    /**
     * Imports translations of terms in the specified vocabulary from the specified file.
     *
     * @param vocabularyIri IRI of vocabulary for whose terms to import translations
     * @param file          File from which to import the translations
     * @return The imported vocabulary metadata
     * @throws cz.cvut.kbss.termit.exception.importing.VocabularyImportException If the import fails
     */
    @PreAuthorize("@vocabularyAuthorizationService.canModify(#vocabularyIri)")
    public Vocabulary importTermTranslations(URI vocabularyIri, MultipartFile file) {
        return repositoryService.importTermTranslations(vocabularyIri, file);
    }

    /**
     * Gets an Excel template file that can be used to import terms into TermIt.
     *
     * @return Template file as a resource
     */
    public TypeAwareResource getExcelTemplateFile() {
        final Configuration config = context.getBean(Configuration.class);
        return config.getTemplate().getExcelImport().map(File::new)
                     .map(f -> (TypeAwareResource) new TypeAwareFileSystemResource(f,
                                                                                   ExportFormat.EXCEL.getMediaType()))
                     .orElseGet(() -> {
                         assert getClass().getClassLoader().getResource("template/termit-import.xlsx") != null;
                         return new TypeAwareClasspathResource("template/termit-import.xlsx",
                                                               ExportFormat.EXCEL.getMediaType());
                     });
    }

    @Override
    public List<AbstractChangeRecord> getChanges(Vocabulary asset, ChangeRecordFilterDto filterDto) {
        return changeRecordService.getChanges(asset, filterDto);
    }

    /**
     * Gets aggregated information about changes in the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose content changes to get
     * @return List of aggregated change objects, ordered by date in ascending order
     */
    public List<AggregatedChangeInfo> getChangesOfContent(Vocabulary vocabulary) {
        return repositoryService.getChangesOfContent(vocabulary);
    }

    /**
     * Gets content change records of the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose content changes to get
     * @param pageReq    Specification of the size and number of the page to return
     * @return List of change records, ordered by date in descending order
     */
    public List<AbstractChangeRecord> getDetailedHistoryOfContent(Vocabulary vocabulary, ChangeRecordFilterDto filter,
                                                                  Pageable pageReq) {
        return repositoryService.getDetailedHistoryOfContent(vocabulary, filter, pageReq);
    }

    /**
     * Runs text analysis on the definitions of all terms in the specified vocabulary, including terms in the
     * transitively imported vocabularies.
     *
     * @param vocabulary Vocabulary to be analyzed
     */
    @Transactional
    @Throttle(value = "{#vocabulary.getUri()}",
              group = "T(ThrottleGroupProvider).getTextAnalysisVocabularyAllTerms(#vocabulary.getUri())",
              name = "allTermsVocabularyAnalysis")
    @PreAuthorize("@vocabularyAuthorizationService.canModify(#vocabulary)")
    public void runTextAnalysisOnAllTerms(Vocabulary vocabulary) {
        vocabulary = findRequired(vocabulary.getUri()); // required when throttling for persistent context
        LOG.debug("Analyzing definitions of all terms in vocabulary {} and vocabularies it imports.", vocabulary);
        SnapshotProvider.verifySnapshotNotModified(vocabulary);
        final List<TermDto> allTerms = termService.findAll(vocabulary);
        getTransitivelyImportedVocabularies(vocabulary).forEach(
                importedVocabulary -> allTerms.addAll(termService.findAll(getReference(importedVocabulary))));
        final Map<TermDto, URI> termsToContexts = new HashMap<>(allTerms.size());
        allTerms.forEach(t -> termsToContexts.put(t, contextMapper.getVocabularyContext(t.getVocabulary())));
        termsToContexts.forEach(termService::analyzeTermDefinition);
    }

    /**
     * Runs text analysis on definitions of all terms in all vocabularies.
     */
    @Throttle(group = "T(ThrottleGroupProvider).getTextAnalysisVocabulariesAll()", name = "allVocabulariesAnalysis")
    @Transactional
    public void runTextAnalysisOnAllVocabularies() {
        LOG.debug("Analyzing definitions of all terms in all vocabularies.");
        final Map<TermDto, URI> termsToContexts = new HashMap<>();
        repositoryService.findAll().forEach(v -> {
            List<TermDto> terms = termService.findAll(new Vocabulary(v.getUri()));
            terms.forEach(t -> termsToContexts.put(t, contextMapper.getVocabularyContext(t.getVocabulary())));
            termsToContexts.forEach(termService::analyzeTermDefinition);
        });
    }

    /**
     * Removes a vocabulary unless:
     * <ul>
     *     <li>it is a document vocabulary or</li>
     *     <li>it is imported by another vocabulary or</li>
     *     <li>it contains terms that are a part of relations with another vocabulary</li>
     * </ul>
     *
     * @param asset Vocabulary to remove
     */
    @Transactional
    @PreAuthorize("@vocabularyAuthorizationService.canRemove(#asset)")
    public void remove(Vocabulary asset) {
        Vocabulary toRemove = repositoryService.findRequired(asset.getUri());
        repositoryService.remove(toRemove);
        aclService.findFor(toRemove).ifPresent(aclService::remove);
    }

    /**
     * Validates a vocabulary: - it checks glossary rules, - it checks OntoUml constraints.
     *
     * @param vocabulary Vocabulary to validate
     */
    public ThrottledFuture<Collection<ValidationResult>> validateContents(URI vocabulary) {
        return repositoryService.validateContents(vocabulary);
    }

    /**
     * Gets the number of terms in the specified vocabulary.
     * <p>
     * Note that this method counts the terms regardless of their hierarchical position.
     *
     * @param vocabulary Vocabulary whose terms should be counted
     * @return Number of terms in the vocabulary, 0 for empty or unknown vocabulary
     */
    public Integer getTermCount(Vocabulary vocabulary) {
        return repositoryService.getTermCount(vocabulary);
    }

    /**
     * Creates a snapshot of the specified vocabulary.
     * <p>
     * The result is a read-only snapshot of the specified vocabulary, its content and any vocabularies it depends on or
     * that depend on it.
     *
     * @param vocabulary Vocabulary to snapshot
     */
    @Transactional
    @PreAuthorize("@vocabularyAuthorizationService.canCreateSnapshot(#vocabulary)")
    public Snapshot createSnapshot(Vocabulary vocabulary) {
        final Snapshot s = getSnapshotCreator().createSnapshot(vocabulary);
        eventPublisher.publishEvent(new VocabularyCreatedEvent(this, s.getUri()));
        cloneAccessControlList(s, vocabulary);
        return s;
    }

    private SnapshotCreator getSnapshotCreator() {
        return context.getBean(SnapshotCreator.class);
    }

    private void cloneAccessControlList(Snapshot snapshot, Vocabulary original) {
        final AccessControlList currentAcl = findRequiredAclForVocabulary(original);
        final AccessControlList snapshotAcl = aclService.clone(currentAcl);
        final Vocabulary snapshotVocabulary = repositoryService.findRequired(snapshot.getUri());
        snapshotVocabulary.setAcl(snapshotAcl.getUri());
    }

    /**
     * Finds snapshots of the specified asset.
     * <p>
     * Note that the list does not contain the currently active version of the asset, as it is not considered a
     * snapshot.
     *
     * @param asset Asset whose snapshots to find
     * @return List of snapshots, sorted by date of creation (latest first)
     */
    public List<Snapshot> findSnapshots(Vocabulary asset) {
        return repositoryService.findSnapshots(asset);
    }

    /**
     * Finds a version of the specified asset valid at the specified instant.
     * <p>
     * The result may be the current version, in case there is no snapshot matching the instant.
     *
     * @param asset Asset whose version to get
     * @param at    Instant at which the asset should be returned
     * @return Version of the asset valid at the specified instant
     */
    public Vocabulary findVersionValidAt(Vocabulary asset, Instant at) {
        return repositoryService.findVersionValidAt(asset, at);
    }

    /**
     * Retrieves {@link AccessControlList} of the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose ACL to retrieve
     * @return Access control list of the specified vocabulary
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@vocabularyAuthorizationService.canManageAccess(#vocabulary)")
    public AccessControlListDto getAccessControlList(Vocabulary vocabulary) {
        return aclService.findForAsDto(vocabulary).orElseThrow(
                () -> new NotFoundException("Access control list for vocabulary " + vocabulary + " not found."));
    }

    private AccessControlList findRequiredAclForVocabulary(Vocabulary vocabulary) {
        return aclService.findFor(vocabulary).orElseThrow(
                () -> new NotFoundException("Access control list for vocabulary " + vocabulary + " not found."));
    }

    /**
     * Adds the specified access control record to the access control list of the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose ACL to update
     * @param record     Record to add to the target ACL
     */
    @Transactional
    @PreAuthorize("@vocabularyAuthorizationService.canManageAccess(#vocabulary)")
    public void addAccessControlRecords(Vocabulary vocabulary, AccessControlRecord<?> record) {
        final AccessControlList acl = findRequiredAclForVocabulary(vocabulary);
        aclService.addRecord(acl, record);
    }

    /**
     * Removes the specified access control record from the access control list of the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose ACL to update
     * @param record     Record to remove from the target ACL
     */
    @Transactional
    @PreAuthorize("@vocabularyAuthorizationService.canManageAccess(#vocabulary)")
    public void removeAccessControlRecord(Vocabulary vocabulary, AccessControlRecord<?> record) {
        final AccessControlList acl = findRequiredAclForVocabulary(vocabulary);
        aclService.removeRecord(acl, record);
    }

    /**
     * Updates access control level in the specified {@link AccessControlRecord}.
     *
     * @param vocabulary Vocabulary whose ACL to update
     * @param update     Access control record containing updated access level
     */
    @Transactional
    @PreAuthorize("@vocabularyAuthorizationService.canManageAccess(#vocabulary)")
    public void updateAccessControlLevel(Vocabulary vocabulary, AccessControlRecord<?> update) {
        final AccessControlList acl = findRequiredAclForVocabulary(vocabulary);
        aclService.updateRecordAccessLevel(acl, update);
    }

    /**
     * Gets the current user's level of access to the specified vocabulary.
     *
     * @param vocabulary Vocabulary access to which is to be examined
     * @return Access level of the current user
     */
    public AccessLevel getAccessLevel(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return authorizationService.getAccessLevel(vocabulary);
    }

    /**
     * Gets the list of languages used in the specified vocabulary.
     *
     * @param vocabularyUri Vocabulary identifier
     * @return List of languages
     */
    @PreAuthorize("@vocabularyAuthorizationService.canRead(#vocabularyUri)")
    public List<String> getLanguages(URI vocabularyUri) {
        return repositoryService.getLanguages(vocabularyUri);
    }

    @Override
    public void setApplicationEventPublisher(@Nonnull ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
}
