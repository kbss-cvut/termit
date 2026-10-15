package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.dto.mapper.DtoMapperImpl;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SkosCollectionRepositoryServiceTest extends BaseServiceTestRunner {
    @Autowired
    private SkosCollectionRepositoryService sut;

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private VocabularyDao vocabularyDao;

    private Vocabulary vocabulary;
    private Descriptor vocabularyDescriptor;

    private List<Term> membersA;
    private TermCollection unorderedCollectionA;

    private List<Term> membersB;
    private OrderedTermCollection orderedCollectionB;

    private final DtoMapper dtoMapper = new DtoMapperImpl();

    @BeforeEach
    void setUp() {
        enableRdfsInference(em);
        vocabulary = Generator.generateVocabularyWithId();
        vocabularyDescriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> vocabularyDao.persist(vocabulary));

        membersA = Generator.generateTermsWithIds(5);
        unorderedCollectionA = Generator.generateTermCollection(vocabulary, membersA);

        membersB = Generator.generateTermsWithIds(5);
        orderedCollectionB = Generator.generateOrderedTermCollection(vocabulary, membersB);

        transactional(() -> {
            Stream.of(membersA, membersB, Set.of(unorderedCollectionA, orderedCollectionB))
                    .flatMap(Collection::stream)
                    .forEach(e -> em.persist(e, vocabularyDescriptor));
        });
    }

    @Test
    void findMembersMapsIdentifiersToRespectiveEntities() {
        final int collectionIndex = 3;
        orderedCollectionB.getMembers().add(collectionIndex, unorderedCollectionA.getUri());
        transactional(() -> em.merge(orderedCollectionB, vocabularyDescriptor));

        final List<HasIdentifier> result =
                new ArrayList<>(sut.findMembers(orderedCollectionB, vocabulary, Pageable.ofSize(100)));

        assertEquals(orderedCollectionB.getMembers().size(), result.size());

        final HasIdentifier nestedCollection = result.remove(collectionIndex);
        assertInstanceOf(SkosCollectionDto.class, nestedCollection);
        final SkosCollectionDto expectedDto = dtoMapper.skosCollectionToDto(unorderedCollectionA);
        expectedDto.setTypes(Set.of()); // JOPA excludes
        assertEquals(expectedDto, nestedCollection);
        final List<TermInfo> expectedTerms =
                membersB.stream().map(Term::toTermInfo).toList();
        assertEquals(expectedTerms, result);
    }
}
