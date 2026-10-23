package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.SkosCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.service.repository.SkosCollectionRepositoryService;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

@Service
public class SkosCollectionService {
    private static final Logger log = LoggerFactory.getLogger(SkosCollectionService.class);
    private final SkosCollectionRepositoryService repositoryService;
    private final VocabularyService vocabularyService;
    private final DtoMapper dtoMapper;

    public SkosCollectionService(
            SkosCollectionRepositoryService repositoryService,
            VocabularyService vocabularyService,
            DtoMapper dtoMapper) {
        this.repositoryService = repositoryService;
        this.vocabularyService = vocabularyService;
        this.dtoMapper = dtoMapper;
    }

    @Transactional(readOnly = true)
    public Page<SkosCollectionDto> findAll(URI vocabularyUri, Pageable pageable) {
        final Vocabulary vocabulary = vocabularyService.getReference(vocabularyUri);
        return repositoryService.findAll(vocabulary, pageable);
    }

    @Transactional
    @PreAuthorize("@vocabularyAuthorizationService.canModify(#skosCollection.vocabulary)")
    public void update(SkosCollectionDto skosCollection) {
        final Vocabulary vocabulary = vocabularyService.getReference(skosCollection.getVocabulary());
        final SkosCollection<?> existing = repositoryService
                .find(skosCollection.getUri(), vocabulary)
                .filter(c -> c.isOrdered() == skosCollection.isOrdered())
                .orElseThrow(() -> notFound(skosCollection.getUri(), vocabulary.getUri()));
        dtoMapper.updateCollectionFromDto(skosCollection, existing);
        repositoryService.update(existing, vocabulary);
    }

    @Transactional
    @PreAuthorize("@vocabularyAuthorizationService.canModify(#skosCollection.vocabulary)")
    public void persist(SkosCollection<?> skosCollection) {
        final Vocabulary vocabulary = vocabularyService.getReference(skosCollection.getVocabulary());
        final String lang = vocabularyService.getPrimaryLanguage(vocabulary.getUri());
        skosCollection.setPrimaryLanguage(lang);
        repositoryService.persist(skosCollection, vocabulary);
    }

    @Transactional(readOnly = true)
    public Page<HasIdentifier> findMembersFromOrdered(URI collectionUri, URI vocabularyUri, Pageable pageable) {
        final Vocabulary vocabulary = vocabularyService.getReference(vocabularyUri);
        final OrderedTermCollection collection = repositoryService
                .findOrdered(collectionUri, vocabulary)
                .orElseThrow(() -> notFound(collectionUri, vocabularyUri));
        return repositoryService.findMembers(collection, pageable);
    }

    @Transactional(readOnly = true)
    public Page<HasIdentifier> findMembersFromUnordered(URI collectionUri, URI vocabularyUri, Pageable pageable) {
        final Vocabulary vocabulary = vocabularyService.getReference(vocabularyUri);
        final TermCollection collection = repositoryService
                .findUnordered(collectionUri, vocabulary)
                .orElseThrow(() -> notFound(collectionUri, vocabularyUri));
        return repositoryService.findMembers(collection, pageable, collection.getPrimaryLanguage());
    }

    @PreAuthorize("@vocabularyAuthorizationService.canRead(#vocabularyUri)")
    @Transactional(readOnly = true)
    public SkosCollectionDto findRequiredDto(URI collectionUri, URI vocabularyUri) {
        return repositoryService
                .findDto(collectionUri)
                .filter(dto -> dto.getVocabulary().equals(vocabularyUri))
                .orElseThrow(() -> notFound(collectionUri, vocabularyUri));
    }

    @PreAuthorize("@vocabularyAuthorizationService.canModify(#vocabularyUri)")
    @Transactional
    public void remove(URI collectionUri, URI vocabularyUri) {
        final Vocabulary vocabulary = vocabularyService.getReference(vocabularyUri);
        final SkosCollection<?> collection = repositoryService
                .find(collectionUri, vocabulary)
                .orElseThrow(() -> notFound(collectionUri, vocabularyUri));
        repositoryService.remove(collection, vocabulary);
    }

    private NotFoundException notFound(URI collectionUri, URI vocabularyUri) {
        return new NotFoundException("Collection " + Utils.uriToString(collectionUri) + " not found in vocabulary "
                + Utils.uriToString(vocabularyUri));
    }

    @PreAuthorize("@vocabularyAuthorizationService.canModify(#vocabularyUri)")
    @Transactional
    public void updateOrderedMemberPosition(
            URI collectionUri, URI vocabularyUri, URI memberIri, int originalPos, int newPos) {
        final Vocabulary vocabulary = vocabularyService.getReference(vocabularyUri);
        final OrderedTermCollection collection = repositoryService
                .findOrdered(collectionUri, vocabulary)
                .orElseThrow(() -> notFound(collectionUri, vocabularyUri));
        repositoryService.updateMemberPosition(collection, vocabulary, memberIri, originalPos, newPos);
        // the update itself is fast, the transaction commit is what takes long
    }

    @PreAuthorize("@vocabularyAuthorizationService.canModify(#vocabularyUri)")
    @Transactional(readOnly = true)
    public Page<HasIdentifier> findAddMemberOptions(URI collectionUri, URI vocabularyUri, Pageable pageRequest) {
        final Vocabulary vocabulary = vocabularyService.getReference(vocabularyUri);
        return repositoryService.findAddMemberOptions(collectionUri, vocabulary, pageRequest);
    }
}
