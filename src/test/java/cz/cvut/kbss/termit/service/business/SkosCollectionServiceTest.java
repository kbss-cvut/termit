package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection_;
import cz.cvut.kbss.termit.model.collection.SkosCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.repository.SkosCollectionRepositoryService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkosCollectionServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private VocabularyDao vocabularyDao;

    @Autowired
    private SkosCollectionService sut;

    @Autowired
    private SkosCollectionRepositoryService repositoryService;

    @Autowired
    private DtoMapper dtoMapper;

    private Vocabulary vocabulary;

    private TermCollection collection;

    @Autowired
    private TermRepositoryService termRepositoryService;

    @BeforeEach
    void setUp() {
        enableRdfsInference(em);
        vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> vocabularyDao.persist(vocabulary));

        collection = Generator.generateTermCollection(vocabulary);
        collection.setMembers(new HashSet<>());
        sut.persist(collection);
    }

    @Test
    void findRequiredDtoThrowsNotFoundExceptionWhenCollectionDoesNotExist() {
        assertThrows(NotFoundException.class, () -> sut.findRequiredDto(Generator.generateUri(), vocabulary.getUri()));
    }

    @Test
    void findMembersThrowsNotFoundExceptionWhenCollectionDoesNotExist() {
        assertThrows(
                NotFoundException.class,
                () -> sut.findMembers(Generator.generateUri(), vocabulary.getUri(), Pageable.ofSize(10)));
    }

    @Test
    void findMembersThrowsNotFoundExceptionWhenVocabularyDoesNotExist() {
        assertThrows(
                NotFoundException.class,
                () -> sut.findMembers(collection.getUri(), Generator.generateUri(), Pageable.ofSize(10)));
    }

    @Test
    void updateOrderedMemberPositionThrowsNotFoundExceptionWhenCollectionDoesNotExist() {
        assertThrows(
                NotFoundException.class,
                () -> sut.updateOrderedMemberPosition(
                        Generator.generateUri(), vocabulary.getUri(), Generator.generateUri(), 0, 1));
    }

    @Test
    void removeThrowsNotFoundExceptionWhenCollectionDoesNotExist() {
        assertThrows(NotFoundException.class, () -> sut.remove(Generator.generateUri(), vocabulary.getUri()));
    }

    @Test
    void addMemberThrowsNotFoundExceptionWhenCollectionDoesNotExist() {
        assertThrows(
                NotFoundException.class,
                () -> sut.addMember(Generator.generateUri(), vocabulary.getUri(), Generator.generateUri()));
    }

    @Test
    void addMemberThrowsNotFoundExceptionWhenMemberDoesNotExist() {
        final URI missingMember = Generator.generateUri();

        final NotFoundException ex = assertThrows(
                NotFoundException.class, () -> sut.addMember(collection.getUri(), vocabulary.getUri(), missingMember));
        assertThat(ex.getMessage()).contains(missingMember.toString());
    }

    @Test
    void addMemberThrowsValidationExceptionWhenCollectionIsAddedToItself() {
        final ValidationException ex = assertThrows(
                ValidationException.class,
                () -> sut.addMember(collection.getUri(), vocabulary.getUri(), collection.getUri()));
        assertThat(ex.getMessage()).containsIgnoringCase("member of itself");
    }

    @Test
    void updateKeepsEntityTypesUnchanged() {
        OrderedTermCollection entity = Generator.generateOrderedTermCollection(vocabulary);
        entity.setMembers(List.of());
        sut.persist(entity);
        final String newLabel = "new label of the collection";

        SkosCollectionDto dto = sut.findRequiredDto(entity.getUri(), vocabulary.getUri());

        dto.getLabel().set(Environment.LANGUAGE, newLabel);
        sut.update(dto);

        SkosCollectionDto updated = sut.findRequiredDto(entity.getUri(), vocabulary.getUri());
        assertEquals(newLabel, updated.getLabel().get(Environment.LANGUAGE));
        assertThat(updated.getTypes()).contains(SKOS.ORDERED_COLLECTION);
    }

    @Test
    void persistSetsPrimaryLanguageForNewCollection() {
        OrderedTermCollection entity = Generator.generateOrderedTermCollection(vocabulary);
        entity.setMembers(List.of());
        entity.setPrimaryLanguage(null);
        assertNull(entity.getPrimaryLanguage());

        sut.persist(entity);

        assertEquals(vocabulary.getPrimaryLanguage(), entity.getPrimaryLanguage());
    }

    @Test
    void updateConvertsUnorderedCollectionToOrdered() {
        List<Term> members = Generator.generateTermsWithIds(5);
        TermCollection unordered = Generator.generateTermCollection(vocabulary, members);
        transactional(() -> members.forEach(t -> termRepositoryService.addRootTermToVocabulary(t, vocabulary)));
        sut.persist(unordered);

        SkosCollectionDto dto = dtoMapper.skosCollectionToDto(unordered);
        dto.setLabel(new MultilingualString());
        dto.getLabel().set(Environment.LANGUAGE, "Updated label");
        dto.addType(OrderedTermCollection_.entityClassIRI.toString());
        assertEquals(unordered.getUri(), dto.getUri());

        sut.update(dto);

        OrderedTermCollection updated =
                repositoryService.findOrdered(dto.getUri(), vocabulary).orElseThrow();
        assertNotEquals(unordered.getLabel(), updated.getLabel());
        assertEquals(dto.getLabel(), updated.getLabel());
        assertEquals(unordered.getVocabulary(), updated.getVocabulary());
        assertEquals(unordered.getPrimaryLanguage(), updated.getPrimaryLanguage());
        assertThat(updated.getMembers()).containsExactlyInAnyOrderElementsOf(unordered.getMembers());
    }

    @Test
    void updateConvertsOrderedCollectionToUnordered() {
        List<Term> members = Generator.generateTermsWithIds(5);
        OrderedTermCollection ordered = Generator.generateOrderedTermCollection(vocabulary, members);
        transactional(() -> members.forEach(t -> termRepositoryService.addRootTermToVocabulary(t, vocabulary)));
        sut.persist(ordered);

        SkosCollectionDto dto = dtoMapper.skosCollectionToDto(ordered);
        dto.removeType(OrderedTermCollection_.entityClassIRI.toString());
        assertEquals(ordered.getUri(), dto.getUri());

        sut.update(dto);

        TermCollection updated =
                repositoryService.findUnordered(dto.getUri(), vocabulary).orElseThrow();
        assertEquals(ordered.getLabel(), updated.getLabel());
        assertEquals(ordered.getVocabulary(), updated.getVocabulary());
        assertEquals(ordered.getPrimaryLanguage(), updated.getPrimaryLanguage());
        assertThat(updated.getMembers()).containsExactlyInAnyOrderElementsOf(ordered.getMembers());
    }

    @Test
    void updateDoesNotRemoveOrderedCollectionFromOtherCollectionsWhenConvertingItsType() {
        OrderedTermCollection ordered = Generator.generateOrderedTermCollection(vocabulary);
        ordered.setMembers(List.of());

        SkosCollectionDto dto = dtoMapper.skosCollectionToDto(ordered);
        dto.removeType(OrderedTermCollection_.entityClassIRI.toString());

        testUpdateDoesNotRemoveCollectionFromOtherCollectionsWhenConvertingItsType(ordered, dto);
    }

    @Test
    void updateDoesNotRemoveUnorderedCollectionFromOtherCollectionsWhenConvertingItsType() {
        TermCollection ordered = Generator.generateTermCollection(vocabulary);
        ordered.setMembers(Set.of());

        SkosCollectionDto dto = dtoMapper.skosCollectionToDto(ordered);
        dto.addType(OrderedTermCollection_.entityClassIRI.toString());

        testUpdateDoesNotRemoveCollectionFromOtherCollectionsWhenConvertingItsType(ordered, dto);
    }

    void testUpdateDoesNotRemoveCollectionFromOtherCollectionsWhenConvertingItsType(
            SkosCollection<?> collection, SkosCollectionDto dto) {
        TermCollection parentCollection = Generator.generateTermCollection(vocabulary);
        OrderedTermCollection parentOrderedCollection = Generator.generateOrderedTermCollection(vocabulary);

        parentCollection.setMembers(Set.of(collection.getUri()));
        parentOrderedCollection.setMembers(List.of(collection.getUri()));

        transactional(() -> sut.persist(collection));

        transactional(() -> {
            sut.persist(parentCollection);
            sut.persist(parentOrderedCollection);
        });

        sut.update(dto);

        SkosCollection<?> converted =
                repositoryService.find(collection.getUri(), vocabulary).orElseThrow();
        assertNotEquals(collection.isOrdered(), converted.isOrdered());

        TermCollection updatedParent = repositoryService
                .findUnordered(parentCollection.getUri(), vocabulary)
                .orElseThrow();
        OrderedTermCollection orderedParent = repositoryService
                .findOrdered(parentOrderedCollection.getUri(), vocabulary)
                .orElseThrow();

        assertThat(updatedParent.getMembers()).containsExactly(collection.getUri());
        assertThat(orderedParent.getMembers()).containsExactly(collection.getUri());
    }
}
