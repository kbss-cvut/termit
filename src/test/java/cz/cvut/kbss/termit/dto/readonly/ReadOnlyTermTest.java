package cz.cvut.kbss.termit.dto.readonly;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ReadOnlyTermTest {

    @Test
    void constructorCopiesAllMappedAttributesFromSpecifiedTerm() {
        final Term term = Generator.generateTermWithId();
        term.setGlossary(Generator.generateUri());
        term.addType(Generator.generateUri().toString());
        term.setSources(Collections.singleton(Generator.generateUri().toString()));
        final Term child = Generator.generateTermWithId();
        term.setSubTerms(Collections.singleton(new TermInfo(child)));
        term.setNotations(Collections.singleton("A-10"));
        term.setExamples(Collections.singleton(MultilingualString.create("Warthog", Environment.LANGUAGE)));

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
        assertEquals(term.getNotations(), result.getNotations());
        assertEquals(term.getExamples(), result.getExamples());
    }

    @Test
    void constructorCopiesFilteredUnMappedAttributesFromSpecifiedTerm() {
        final URI u1 = Generator.generateUri();
        final URI u2 = Generator.generateUri();
        final URI u3 = Generator.generateUri();

        final Term term = Generator.generateTermWithId();
        final Map<String, Set<String>> properties = new HashMap<>();
        properties.put(u1.toString(), Collections.singleton("a"));
        properties.put(u2.toString(), Collections.singleton("b"));

        term.setProperties(properties);

        final Set<String> whiteList = new HashSet<>();
        whiteList.add(u2.toString());
        whiteList.add(u3.toString());

        final ReadOnlyTerm result = new ReadOnlyTerm(term, whiteList);
        assertEquals(1, result.getProperties().size());
        assertEquals(Collections.singleton(u2.toString()), result.getProperties().keySet());
        assertEquals(Collections.singleton("b"), result.getProperties().get(u2.toString()));
    }

    @Test
    void constructorCopiesParentTermsAsReadonly() {
        final Term term = Generator.generateTermWithId();
        final Term parent = Generator.generateTermWithId();
        term.setParentTerms(Collections.singleton(parent));

        final ReadOnlyTerm result = new ReadOnlyTerm(term);
        assertEquals(Collections.singleton(new ReadOnlyTerm(parent)), result.getParentTerms());
    }

    @Test
    void isSnapshotReturnsTrueWhenInstanceHasSnapshotType() {
        final ReadOnlyTerm original = new ReadOnlyTerm(Generator.generateTermWithId());
        final ReadOnlyTerm snapshot = new ReadOnlyTerm(Generator.generateTermWithId());
        snapshot.addType(Vocabulary.s_c_verze_pojmu);
        assertFalse(original.isSnapshot());
        assertTrue(snapshot.isSnapshot());
    }
}
