package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
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

    @Autowired
    private DtoMapper dtoMapper;

    private Vocabulary vocabulary;
    private Descriptor vocabularyDescriptor;

    private List<Term> unorderedMembers;
    private TermCollection unorderedCollection;

    private List<Term> orderedMembers;
    private OrderedTermCollection orderedCollection;

    @BeforeEach
    void setUp() {
        enableRdfsInference(em);
        vocabulary = Generator.generateVocabularyWithId();
        vocabularyDescriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> vocabularyDao.persist(vocabulary));

        unorderedMembers = Generator.generateTermsWithIds(5);
        unorderedCollection = Generator.generateTermCollection(vocabulary, unorderedMembers);

        orderedMembers = Generator.generateTermsWithIds(5);
        orderedCollection = Generator.generateOrderedTermCollection(vocabulary, orderedMembers);

        transactional(() -> {
            Stream.of(unorderedMembers, orderedMembers, Set.of(unorderedCollection, orderedCollection))
                    .flatMap(Collection::stream)
                    .forEach(e -> em.persist(e, vocabularyDescriptor));
        });
    }

    @Test
    void findMembersMapsIdentifiersToRespectiveEntities() {
        final int collectionIndex = 3;
        orderedCollection.getMembers().add(collectionIndex, unorderedCollection.getUri());
        transactional(() -> em.merge(orderedCollection, vocabularyDescriptor));

        final List<HasIdentifier> result =
                new ArrayList<>(sut.findMembers(orderedCollection, vocabulary, Pageable.ofSize(100)));

        assertEquals(orderedCollection.getMembers().size(), result.size());

        final HasIdentifier nestedCollection = result.remove(collectionIndex);
        assertInstanceOf(SkosCollectionDto.class, nestedCollection);
        final SkosCollectionDto expectedDto = dtoMapper.skosCollectionToDto(unorderedCollection);
        expectedDto.setTypes(Set.of()); // JOPA excludes
        assertEquals(expectedDto, nestedCollection);
        final List<TermInfo> expectedTerms =
                orderedMembers.stream().map(Term::toTermInfo).toList();
        assertEquals(expectedTerms, result);
    }
}
