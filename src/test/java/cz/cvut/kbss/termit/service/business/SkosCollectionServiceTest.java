package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkosCollectionServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private VocabularyDao vocabularyDao;

    @Autowired
    private SkosCollectionService sut;

    private Vocabulary vocabulary;

    private TermCollection collection;

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
    void findMembersFromUnorderedThrowsNotFoundExceptionWhenCollectionDoesNotExist() {
        assertThrows(
                NotFoundException.class,
                () -> sut.findMembersFromUnordered(Generator.generateUri(), vocabulary.getUri(), Pageable.ofSize(10)));
    }

    @Test
    void findMembersFromOrderedThrowsNotFoundExceptionWhenCollectionDoesNotExist() {
        assertThrows(
                NotFoundException.class,
                () -> sut.findMembersFromOrdered(Generator.generateUri(), vocabulary.getUri(), Pageable.ofSize(10)));
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
}
