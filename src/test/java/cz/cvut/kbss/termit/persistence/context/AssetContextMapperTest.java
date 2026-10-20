package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.util.Pair;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetContextMapperTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private AssetContextMapper sut;

    @ParameterizedTest
    @MethodSource("argumentGenerator")
    void getContextResolvesAssetContext(Function<AssetContextMapperTest, Pair<URI, URI>> generator) {
        final Pair<URI, URI> testData = generator.apply(this);
        assertEquals(testData.getSecond(), sut.getContext(testData.getFirst()));
    }

    static Stream<Arguments> argumentGenerator() {
        return Stream.of(
                Arguments.of(
                        (Function<AssetContextMapperTest, Pair<URI, URI>>) AssetContextMapperTest::generateVocabulary),
                Arguments.of((Function<AssetContextMapperTest, Pair<URI, URI>>) AssetContextMapperTest::generateTerm),
                Arguments.of(
                        (Function<AssetContextMapperTest, Pair<URI, URI>>) AssetContextMapperTest::generateDocument),
                Arguments.of((Function<AssetContextMapperTest, Pair<URI, URI>>) AssetContextMapperTest::generateFile));
    }

    private Pair<URI, URI> generateVocabulary() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(v, descriptorFactory.vocabularyDescriptor(v)));
        return new Pair<>(v.getUri(), v.getUri());
    }

    private Pair<URI, URI> generateTerm() {
        final Vocabulary owner = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(owner.getUri());
        transactional(() -> {
            em.persist(owner, descriptorFactory.vocabularyDescriptor(owner));
            em.persist(term, descriptorFactory.termDescriptor(term));
        });
        return new Pair<>(term.getUri(), owner.getUri());
    }

    private Pair<URI, URI> generateDocument() {
        final Vocabulary owner = Generator.generateVocabularyWithId();
        final Document document = Generator.generateDocumentWithId();
        owner.setDocument(document);
        transactional(() -> {
            em.persist(owner, descriptorFactory.vocabularyDescriptor(owner));
            em.persist(document, descriptorFactory.documentDescriptor(owner));
        });
        return new Pair<>(document.getUri(), owner.getUri());
    }

    private Pair<URI, URI> generateFile() {
        final Vocabulary owner = Generator.generateVocabularyWithId();
        final Document document = Generator.generateDocumentWithId();
        owner.setDocument(document);
        final File file = Generator.generateFileWithId("test.html");
        document.addFile(file);
        transactional(() -> {
            em.persist(owner, descriptorFactory.vocabularyDescriptor(owner));
            em.persist(document, descriptorFactory.documentDescriptor(owner));
            em.persist(file, descriptorFactory.fileDescriptor(owner));
        });
        return new Pair<>(file.getUri(), owner.getUri());
    }
}
