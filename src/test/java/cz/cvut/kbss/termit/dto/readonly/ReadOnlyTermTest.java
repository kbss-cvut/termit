package cz.cvut.kbss.termit.dto.readonly;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ReadOnlyTermTest {

    @Test
    void constructorCopiesAllAttributesFromSpecifiedTerm() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(Generator.generateUri());
        term.addType(Generator.generateUri().toString());
        term.setSources(Collections.singleton(Generator.generateUri().toString()));
        final Term child = Generator.generateTermWithId();
        term.setSubTerms(Collections.singleton(new TermInfo(child)));

        final ReadOnlyTerm result = new ReadOnlyTerm(term);
        assertEquals(term.getUri(), result.getUri());
        assertEquals(term.getLabel(), result.getLabel());
        assertEquals(term.getAltLabels(), result.getAltLabels());
        assertEquals(term.getHiddenLabels(), result.getHiddenLabels());
        assertEquals(term.getDefinition(), result.getDefinition());
        assertEquals(term.getDescription(), result.getDescription());
        assertEquals(term.getSources(), result.getSources());
        assertEquals(term.getVocabulary(), result.getVocabulary());
        assertEquals(term.getTypes(), result.getTypes());
        assertEquals(term.getSubTerms(), result.getSubTerms());
        assertEquals(term.getGlossary(), result.getGlossary());
        assertEquals(term.getExactMatchTerms(), result.getExactMatchTerms());
        assertEquals(term.getRelated(), result.getRelated());
        assertEquals(term.getRelatedMatch(), result.getRelatedMatch());
    }

    @Test
    void constructorCopiesParentTermsAsReadonly() {
        final Term term = Generator.generateTermWithId();
        final Term parent = Generator.generateTermWithId();
        term.setParentTerms(Collections.singleton(parent));

        final ReadOnlyTerm result = new ReadOnlyTerm(term);
        assertEquals(Collections.singleton(new ReadOnlyTerm(parent)), result.getParentTerms());
    }
}
