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
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.asset.provenance.SupportsLastModification;
import cz.cvut.kbss.termit.dto.assignment.ResourceTermAssignments;
import cz.cvut.kbss.termit.event.DocumentRenameEvent;
import cz.cvut.kbss.termit.event.FileRenameEvent;
import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedAssetOperationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermAssignment;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.service.changetracking.ChangeRecordProvider;
import cz.cvut.kbss.termit.service.document.DocumentManager;
import cz.cvut.kbss.termit.service.document.TextAnalysisService;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.ResourceRepositoryService;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URI;
import java.util.*;

/**
 * Interface of business logic concerning resources.
 */
@Service
public class ResourceService
        implements CrudService<Resource>, SupportsLastModification, ChangeRecordProvider<Resource>, ApplicationEventPublisherAware {

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
     * Sets terms with which the specified target resource is annotated.
     *
     * @param target   Target resource
     * @param termUris Identifiers of terms annotating the resource
     */
    @Transactional
    public void setTags(Resource target, Collection<URI> termUris) {
        repositoryService.setTags(target, termUris);
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
    public void remove(Resource toRemove) {
        Objects.requireNonNull(toRemove);
        // We need the reference managed, so that its name is available to document manager
        final Resource actualToRemove = getRequiredReference(toRemove.getUri());
        documentManager.remove(actualToRemove);
        repositoryService.remove(actualToRemove);
    }

    /**
     * Gets terms the specified resource is annotated with.
     *
     * @param resource Annotated resource
     * @return List of terms annotating the specified resource
     */
    public List<Term> findTags(Resource resource) {
        return repositoryService.findTags(resource);
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
        return repositoryService.findAssignments(resource);
    }

    /**
     * Gets aggregate information about Term assignments/occurrences for the specified Resource.
     *
     * @param resource Resource to get assignments for
     * @return Aggregate assignment data
     */
    public List<ResourceTermAssignments> getAssignmentInfo(Resource resource) {
        return repositoryService.getAssignmentInfo(resource);
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
     *
     * @param resource Resource whose content should be retrieved
     * @return Representation of the resource content
     * @throws UnsupportedAssetOperationException When content of the specified resource cannot be retrieved
     */
    public TypeAwareResource getContent(Resource resource) {
        Objects.requireNonNull(resource);
        if (!(resource instanceof File)) {
            throw new UnsupportedAssetOperationException("Content retrieval is not supported for resource " + resource);
        }
        return documentManager.getAsResource((File) resource);
    }

    /**
     * Saves content of the specified resource.
     *
     * @param resource Domain resource associated with the content
     * @param content  Resource content
     * @throws UnsupportedAssetOperationException If content saving is not supported for the specified resource
     */
    @Transactional
    public void saveContent(Resource resource, InputStream content) {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(content);
        if (!(resource instanceof File)) {
            throw new UnsupportedAssetOperationException("Content saving is not supported for resource " + resource);
        }
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
        if (!(instance instanceof Document)) {
            throw new UnsupportedAssetOperationException("Cannot get files from resource which is not a document.");
        }
        final Document doc = (Document) instance;
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
    public void addFileToDocument(Resource document, File file) {
        Objects.requireNonNull(document);
        Objects.requireNonNull(file);
        if (!(document instanceof Document)) {
            throw new UnsupportedAssetOperationException("Cannot add file to the specified resource " + document);
        }
        final Document doc = (Document) document;
        doc.addFile(file);
        if (doc.getVocabulary() != null) {
            final Vocabulary vocabulary = vocabularyService.getRequiredReference(doc.getVocabulary());
            repositoryService.persist(file, vocabulary);
        } else {
            persist(file);
        }
        if ( !getReference(document.getUri()).isPresent() ) {
            persist(document);
        } else {
            update(doc);
        }
    }

    /**
     * Removes the file. The file is detached from the document and removed, together with its content.
     *
     * @param file     The file to add and save
     * @throws UnsupportedAssetOperationException If the specified resource is not a Document
     */
    @Transactional
    public void removeFile(File file) {
        Objects.requireNonNull(file);
        final Document doc = file.getDocument();
        if (doc == null) {
            throw new InvalidParameterException("File was not attached to a document.");
        } else {
            doc.removeFile(file);
            if ( repositoryService.getReference(doc.getUri()).isPresent() ) {
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
    public void runTextAnalysis(Resource resource, Set<URI> vocabularies) {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(vocabularies);
        if (!(resource instanceof File)) {
            throw new UnsupportedAssetOperationException("Text analysis is not supported for resource " + resource);
        }
        LOG.trace("Invoking text analysis on resource {}.", resource);
        final File file = (File) resource;
        if (vocabularies.isEmpty()) {
            if (file.getDocument() == null || file.getDocument().getVocabulary() == null) {
                throw new UnsupportedAssetOperationException(
                        "Cannot analyze file without specifying vocabulary context.");
            }
            textAnalysisService.analyzeFile(file,
                    includeImportedVocabularies(Collections.singleton(file.getDocument().getVocabulary())));
        } else {
            textAnalysisService.analyzeFile(file, includeImportedVocabularies(vocabularies));
        }
    }

    private Set<URI> includeImportedVocabularies(Set<URI> providedVocabularies) {
        final Set<URI> result = new HashSet<>(providedVocabularies);
        providedVocabularies.forEach(uri -> {
            final Vocabulary ref = vocabularyService.getRequiredReference(uri);
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

    @Override
    public List<Resource> findAll() {
        return repositoryService.findAll();
    }

    @Override
    public Optional<Resource> find(URI id) {
        return repositoryService.find(id);
    }

    @Override
    public Resource findRequired(URI id) {
        return repositoryService.findRequired(id);
    }

    @Override
    public Optional<Resource> getReference(URI id) {
        return repositoryService.getReference(id);
    }

    @Override
    public Resource getRequiredReference(URI id) {
        return repositoryService.getRequiredReference(id);
    }

    @Override
    public boolean exists(URI id) {
        return repositoryService.exists(id);
    }

    @Transactional
    @Override
    public void persist(Resource instance) {
        repositoryService.persist(instance);
    }

    @Transactional
    @Override
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
                return Optional.of(new FileRenameEvent((File) instance, original.getLabel(),
                    instance.getLabel()));
            }
        } else if (instance instanceof Document ) {
            final Resource original = findRequired(instance.getUri());
            if (!Objects.equals(original.getLabel(), instance.getLabel())) {
                return Optional.of(new DocumentRenameEvent((Document) instance, original.getLabel(),
                    instance.getLabel()));
            }
        }
        return Optional.empty();
    }

    @Override
    public long getLastModified() {
        return repositoryService.getLastModified();
    }

    @Override
    public List<AbstractChangeRecord> getChanges(Resource asset) {
        return changeRecordService.getChanges(asset);
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
}
