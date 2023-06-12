package cz.cvut.kbss.termit.dto.mapper;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetToRdfsResourceMapperTest {

    private final AssetToRdfsResourceMapper sut = new AssetToRdfsResourceMapper(Environment.LANGUAGE);

    @Test
    void visitTermTransformsTermToRdfsResource() {
        final Term t = Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs");
        t.accept(sut);
        assertEquals(sut.getRdfsResource().getUri(), t.getUri());
        assertEquals(sut.getRdfsResource().getLabel(), t.getLabel());
        assertEquals(sut.getRdfsResource().getComment(), t.getDefinition());
        assertThat(sut.getRdfsResource().getTypes(), hasItem(SKOS.CONCEPT));
    }

    @Test
    void visitTermAddsTermTypesToTargetRdfsResource() {
        final Term t = Generator.generateTermWithId();
        t.addType(Generator.generateUri().toString());
        t.accept(sut);
        assertThat(sut.getRdfsResource().getTypes(), hasItems(t.getTypes().toArray(new String[]{})));
    }

    @Test
    void visitVocabularyTransformsVocabularyToRdfsResource() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        v.accept(sut);
        assertEquals(sut.getRdfsResource().getUri(), v.getUri());
        assertEquals(sut.getRdfsResource().getLabel(), MultilingualString.create(v.getLabel(), Environment.LANGUAGE));
        assertThat(sut.getRdfsResource().getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik));
    }

    @Test
    void visitVocabularyTransformsResourceToRdfsResource() {
        final Document d = Generator.generateDocumentWithId();
        d.accept(sut);
        assertEquals(sut.getRdfsResource().getUri(), d.getUri());
        assertEquals(sut.getRdfsResource().getLabel(), MultilingualString.create(d.getLabel(), Environment.LANGUAGE));
        assertThat(sut.getRdfsResource().getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_dokument));
    }
}
