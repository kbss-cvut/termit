package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SkosCollectionServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private VocabularyDao vocabularyDao;

    @Autowired
    private SkosCollectionService sut;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        enableRdfsInference(em);
        vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> vocabularyDao.persist(vocabulary));
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
}
