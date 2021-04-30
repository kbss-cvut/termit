package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TermDtoTest {

    @Test
    void constructorConsolidatesRelatedAndInverseRelatedTermsIntoRelatedTermsAttribute() {
        final Term source = Generator.generateMultiLingualTerm(Constants.DEFAULT_LANGUAGE, "cs");
        source.setVocabulary(Generator.generateUri());
        source.setRelated(IntStream.range(0,5).mapToObj(i -> new TermInfo(Generator.generateTermWithId(source.getVocabulary()))).collect(Collectors.toSet()));
        source.setInverseRelated(IntStream.range(0,5).mapToObj(i -> new TermInfo(Generator.generateTermWithId(source.getVocabulary()))).collect(Collectors.toSet()));

        final TermDto sut = new TermDto(source);
        assertEquals(source.getUri(), sut.getUri());
        assertEquals(source.getLabel(), sut.getLabel());
        assertEquals(source.getDefinition(), sut.getDefinition());
        assertEquals(source.getDescription(), sut.getDescription());
        assertEquals(source.getRelated().size() + source.getInverseRelated().size(), sut.getRelated().size());
        source.getRelated().forEach(ti -> assertThat(sut.getRelated(), hasItem(ti)));
        source.getInverseRelated().forEach(ti -> assertThat(sut.getRelated(), hasItem(ti)));
    }

    @Test
    void constructorConsolidatesRelatedMatchAndInverseRelatedMatchTermsIntoRelatedMatchAttribute() {
        final Term source = Generator.generateTermWithId();
        source.setRelatedMatch(IntStream.range(0,5).mapToObj(i -> new TermInfo(Generator.generateTermWithId(Generator.generateUri()))).collect(Collectors.toSet()));
        source.setInverseRelatedMatch(IntStream.range(0,5).mapToObj(i -> new TermInfo(Generator.generateTermWithId(Generator.generateUri()))).collect(Collectors.toSet()));

        final TermDto sut = new TermDto(source);
        assertEquals(source.getRelatedMatch().size() + source.getInverseRelatedMatch().size(), sut.getRelatedMatch().size());
        source.getRelatedMatch().forEach(ti -> assertThat(sut.getRelatedMatch(), hasItem(ti)));
        source.getInverseRelatedMatch().forEach(ti -> assertThat(sut.getRelatedMatch(), hasItem(ti)));
    }

    @Test
    void constructorHandlesTermWithoutRelatedAndRelatedMatchTerms() {
        final Term source = Generator.generateTermWithId();
        final TermDto sut = new TermDto(source);

        assertThat(sut.getRelated(), anyOf(emptyCollectionOf(TermInfo.class), nullValue()));
        assertThat(sut.getRelatedMatch(), anyOf(emptyCollectionOf(TermInfo.class), nullValue()));
    }
}