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
import cz.cvut.kbss.termit.dto.filter.ChangeRecordFilterDto;
import cz.cvut.kbss.termit.event.DocumentRenameEvent;
import cz.cvut.kbss.termit.event.FileRenameEvent;
import cz.cvut.kbss.termit.event.VocabularyWillBeRemovedEvent;
import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedAssetOperationException;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.service.changetracking.ChangeRecordProvider;
import cz.cvut.kbss.termit.service.document.DocumentManager;
import cz.cvut.kbss.termit.service.document.ResourceRetrievalSpecification;
import cz.cvut.kbss.termit.service.document.TextAnalysisService;
import cz.cvut.kbss.termit.service.document.html.UnconfirmedTermOccurrenceRemover;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.ResourceRepositoryService;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Interface of business logic concerning resources.
 */
@Service
public class ResourceService
        implements SupportsLastModification, ChangeRecordProvider<Resource>, ApplicationEventPublisherAware {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceService.class);

    private final ResourceRepositoryService repositoryService;

    private final DocumentManager documentManager;

    private final TextAnalysisService textAnalysisService;

    private final VocabularyService vocabularyService;

    private final ChangeRecordService changeRecordService;

    private ApplicationEventPublisher eventPublisher;

    @Autowired
    public ResourceService(ResourceRepositoryService repositoryService, DocumentManager documentManager,
                           TextAnalysisService textAnalysisService, VocabularyService vocabularyService,
                           ChangeRecordService changeRecordService) {
        this.repositoryService = repositoryService;
        this.documentManager = documentManager;
        this.textAnalysisService = textAnalysisService;
        this.vocabularyService = vocabularyService;
        this.changeRecordService = changeRecordService;
    }

    /**
     * Ensures that document gets removed during Vocabulary removal
     */
    @EventListener
    public void onVocabularyRemoval(VocabularyWillBeRemovedEvent event) {
        vocabularyService.find(event.getVocabularyIri()).ifPresent(vocabulary -> {
            if(vocabulary.getDocument() != null) {
                remove(vocabulary.getDocument());
            }
        });
    }

    /**
     * Removes resource with the specified identifier.
     * <p>
     * Resource removal also involves cleanup of annotations and term occurrences associated with it.
     * <p>
     *
     * @param toRemove a resource to remove
     */
    @Transactional
    @PreAuthorize("@resourceAuthorizationService.canRemove(#toRemove)")
    public void remove(Resource toRemove) {
        Objects.requireNonNull(toRemove);
        final Resource managed = findRequired(toRemove.getUri());
        if (managed instanceof Document doc) {
            doc.getFiles().forEach(f -> {
                documentManager.remove(f);
                repositoryService.remove(f);
            });
        }
        // We need the reference managed, so that its name is available to document manager
        documentManager.remove(managed);
        repositoryService.remove(managed);
    }

    /**
     * Checks whether content is stored for the specified resource.
     * <p>
     * Note that content is typically stored only for {@link File}s, so this method will return false for any other type
     * of {@link Resource}.
     *
     * @param resource Resource whose content existence is to be verified
     * @return Whether content is stored
     */
    public boolean hasContent(Resource resource) {
        Objects.requireNonNull(resource);
        return (resource instanceof File) && documentManager.exists((File) resource);
    }

    /**
     * Gets content of the specified resource.
     * <p>
     * The {@link ResourceRetrievalSpecification} argument provides further parameterization of the content to
     * retrieve.
     * <p>
     * If the timestamp specified by {@code retrievalSpecification} is older than the first version of the specified
     * resource, this version is returned. Similarly, if the timestamp is later than the most recent backup of the
     * resource, the current version is returned.
     *
     * @param resource               Resource whose content should be retrieved
     * @param retrievalSpecification Specification of the result
     * @return Representation of the resource content
     * @throws UnsupportedAssetOperationException When content of the specified resource cannot be retrieved
     * @throws NotFoundException                  When the specified resource has no content stored
     */
    public TypeAwareResource getContent(Resource resource, ResourceRetrievalSpecification retrievalSpecification) {
        Objects.requireNonNull(resource);
        verifyFileOperationPossible(resource, "Content retrieval");
        final File file = (File) resource;
        TypeAwareResource result = retrievalSpecification.at()
                                                         .map(instant -> documentManager.getAsResource(file, instant))
                                                         .orElseGet(() -> documentManager.getAsResource(file));
        if (retrievalSpecification.withoutUnconfirmedOccurrences()) {
            result = new UnconfirmedTermOccurrenceRemover().removeUnconfirmedOccurrences(result);
        }
        return result;
    }

    private void verifyFileOperationPossible(Resource resource, String operation) {
        if (!(resource instanceof File)) {
            throw new UnsupportedAssetOperationException(operation + " is not supported for resource " + resource);
        }
    }

    /**
     * Saves content of the specified resource.
     *
     * @param resource Domain resource associated with the content
     * @param content  Resource content
     * @throws UnsupportedAssetOperationException If content saving is not supported for the specified resource
     */
    @Transactional
    @PreAuthorize("@resourceAuthorizationService.canModify(#resource)")
    public void saveContent(Resource resource, InputStream content) {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(content);
        verifyFileOperationPossible(resource, "Content saving");
        LOG.trace("Saving new content of resource {}.", resource);
        final File file = (File) resource;
        if (documentManager.exists(file)) {
            documentManager.createBackup(file);
        }
        documentManager.saveFileContent(file, content);
    }

    /**
     * Retrieves files contained in the specified document.
     * <p>
     * The files are sorted by their label (ascending).
     *
     * @param document Document resource
     * @return Files in the document
     * @throws UnsupportedAssetOperationException If the specified instance is not a Document
     */
    public List<File> getFiles(Resource document) {
        Objects.requireNonNull(document);
        final Resource instance = findRequired(document.getUri());
        if (!(instance instanceof Document doc)) {
            throw new UnsupportedAssetOperationException("Cannot get files from resource which is not a document.");
        }
        if (doc.getFiles() != null) {
            final List<File> list = new ArrayList<>(doc.getFiles());
            list.sort(Comparator.comparing(File::getLabel));
            return list;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Adds the specified file to the specified document and persists it.
     *
     * @param document Document into which the file should be added
     * @param file     The file to add and save
     * @throws UnsupportedAssetOperationException If the specified resource is not a Document
     */
    @Transactional
    @PreAuthorize("@resourceAuthorizationService.canModify(#document)")
    public void addFileToDocument(Resource document, File file) {
        Objects.requireNonNull(document);
        Objects.requireNonNull(file);
        if (!(document instanceof Document doc)) {
            throw new UnsupportedAssetOperationException("Cannot add file to the specified resource " + document);
        }
        doc.addFile(file);
        if (doc.getVocabulary() != null) {
            final Vocabulary vocabulary = vocabularyService.getReference(doc.getVocabulary());
            repositoryService.persist(file, vocabulary);
        } else {
            repositoryService.persist(file);
        }
        if (!repositoryService.exists(document.getUri())) {
            repositoryService.persist(document);
        } else {
            update(doc);
        }
    }

    /**
     * Removes the file. The file is detached from the document and removed, together with its content.
     *
     * @param file The file to add and save
     * @throws UnsupportedAssetOperationException If the specified resource is not a Document
     */
    @Transactional
    @PreAuthorize("@resourceAuthorizationService.canRemove(#file)")
    public void removeFile(File file) {
        Objects.requireNonNull(file);
        final Document doc = file.getDocument();
        if (doc == null) {
            throw new InvalidParameterException("File was not attached to a document.");
        } else {
            doc.removeFile(file);
            if (repositoryService.find(doc.getUri()).isPresent()) {
                update(doc);
            }
        }
        documentManager.remove(file);
        repositoryService.remove(file);
    }

    /**
     * Executes text analysis on the specified resource's content.
     * <p>
     * The specified vocabulary identifiers represent sources of Terms for the text analysis. If not provided, it is
     * assumed the file belongs to a Document associated with a Vocabulary which will be used as the Term source.
     *
     * @param resource     Resource to analyze
     * @param vocabularies Set of identifiers of vocabularies to use as Term sources for the analysis. Possibly empty
     * @throws UnsupportedAssetOperationException If text analysis is not supported for the specified resource
     */
    @PreAuthorize("@resourceAuthorizationService.canModify(#resource)")
    public void runTextAnalysis(Resource resource, Set<URI> vocabularies) {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(vocabularies);
        verifyFileOperationPossible(resource, "Text analysis");
        LOG.trace("Invoking text analysis on resource {}.", resource);
        final File file = (File) resource;
        if (vocabularies.isEmpty()) {
            if (file.getDocument() == null || file.getDocument().getVocabulary() == null) {
                throw new UnsupportedAssetOperationException(
                        "Cannot analyze file without specifying vocabulary context.");
            }
            textAnalysisService.analyzeFile(file,
                                            includeImportedVocabularies(
                                                    Collections.singleton(file.getDocument().getVocabulary())));
        } else {
            textAnalysisService.analyzeFile(file, includeImportedVocabularies(vocabularies));
        }
    }

    private Set<URI> includeImportedVocabularies(Set<URI> providedVocabularies) {
        final Set<URI> result = new HashSet<>(providedVocabularies);
        providedVocabularies.forEach(uri -> {
            final Vocabulary ref = vocabularyService.getReference(uri);
            result.addAll(vocabularyService.getTransitivelyImportedVocabularies(ref));
        });
        return result;
    }

    /**
     * Gets the latest {@link TextAnalysisRecord} for the specified Resource.
     *
     * @param resource Analyzed Resource
     * @return Latest text analysis record
     * @throws NotFoundException When no text analysis record exists for the specified resource
     */
    public TextAnalysisRecord findLatestTextAnalysisRecord(Resource resource) {
        return textAnalysisService.findLatestAnalysisRecord(resource).orElseThrow(
                () -> new NotFoundException("No text analysis record exists for " + resource));
    }

    public Resource findRequired(URI id) {
        return repositoryService.findRequired(id);
    }

    public Resource getReference(URI id) {
        return repositoryService.getReference(id);
    }

    @Transactional
    @PreAuthorize("@resourceAuthorizationService.canModify(#instance)")
    public Resource update(Resource instance) {
        final Optional<ApplicationEvent> evt = createFileOrDocumentLabelUpdateNotification(instance);
        final Resource result = repositoryService.update(instance);
        // Notify only after update in repository to ensure that the change has succeeded
        // Note that since this is happening in the same transaction, we are relying on the hypothetical exception
        // being thrown on merge, not on commit
        // If an exception is thrown on commit, the event cannot be reverted
        evt.ifPresent(eventPublisher::publishEvent);
        return result;
    }

    private Optional<ApplicationEvent> createFileOrDocumentLabelUpdateNotification(Resource instance) {
        if (instance instanceof File) {
            final Resource original = findRequired(instance.getUri());
            if (!Objects.equals(original.getLabel(), instance.getLabel())) {
                return Optional.of(new FileRenameEvent((File) instance, original.getLabel(), instance.getLabel()));
            }
        } else if (instance instanceof Document) {
            final Resource original = findRequired(instance.getUri());
            if (!Objects.equals(original.getLabel(), instance.getLabel())) {
                return Optional.of(
                        new DocumentRenameEvent((Document) instance, original.getLabel(), instance.getLabel()));
            }
        }
        return Optional.empty();
    }

    @Override
    public long getLastModified() {
        return repositoryService.getLastModified();
    }

    @Override
    public List<AbstractChangeRecord> getChanges(Resource asset, ChangeRecordFilterDto filterDto) {
        return changeRecordService.getChanges(asset, filterDto);
    }

    @Override
    public void setApplicationEventPublisher(@Nonnull ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
}
