package cz.cvut.kbss.termit.service.term;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AssertedInferredValueDifferentiatorTest {

    private final AssertedInferredValueDifferentiator sut = new AssertedInferredValueDifferentiator();

    @Test
    void differentiateRelatedTermsHandlesTermsWithoutRelated() {
        final Term target = Generator.generateTermWithId();
        final Term original = new Term();
        original.setUri(target.getUri());
        original.setDefinition(original.getDefinition());
        sut.differentiateRelatedTerms(target, original);
        assertThat(target.getRelated(), anyOf(emptyCollectionOf(TermInfo.class), nullValue()));
        assertThat(target.getInverseRelated(), anyOf(emptyCollectionOf(TermInfo.class), nullValue()));
    }

    @Test
    void differentiateRelatedTermsMovesTermsKnownToBeInferredInOriginalToInferredInTarget() {
        final Term target = Generator.generateTermWithId();
        final Set<TermInfo> asserted = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId())).collect(Collectors.toSet());
        final Set<TermInfo> inferred = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId())).collect(Collectors.toSet());
        final Set<TermInfo> consolidated = new HashSet<>(asserted);
        consolidated.addAll(inferred);
        target.setRelated(consolidated);
        final Term original = new Term();
        original.setRelated(new HashSet<>(asserted));
        original.setInverseRelated(new HashSet<>(inferred));
        sut.differentiateRelatedTerms(target, original);
        assertEquals(asserted, target.getRelated());
        assertEquals(inferred, target.getInverseRelated());
    }

    @Test
    void differentiateRelatedTermsHandlesChangesToIncomingRelatedTerms() {
        final Term target = Generator.generateTermWithId();
        final Set<TermInfo> asserted = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId())).collect(Collectors.toSet());
        final Set<TermInfo> inferred = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId())).collect(Collectors.toSet());
        final Set<TermInfo> newAsserted = new HashSet<>(asserted);
        // Remove one asserted and add a different one
        newAsserted.remove(asserted.iterator().next());
        newAsserted.add(new TermInfo(Generator.generateTermWithId()));
        final Set<TermInfo> consolidated = new HashSet<>(newAsserted);
        consolidated.addAll(inferred);
        target.setRelated(consolidated);
        final Term original = new Term();
        original.setRelated(new HashSet<>(asserted));
        original.setInverseRelated(new HashSet<>(inferred));
        sut.differentiateRelatedTerms(target, original);
        assertEquals(inferred, target.getInverseRelated());
        assertEquals(asserted.size(), target.getRelated().size());
        inferred.forEach(ti -> assertThat(target.getRelated(), not(hasItem(ti))));
        assertEquals(newAsserted, target.getRelated());
    }

    @Test
    void differentiateRelatedMatchHandlesMovesTermsKnownToBeInferredInOriginalToInferredInTarget() {
        final Term target = Generator.generateTermWithId();
        final Set<TermInfo> asserted = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId())).collect(Collectors.toSet());
        final Set<TermInfo> inferred = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId())).collect(Collectors.toSet());
        final Set<TermInfo> consolidated = new HashSet<>(asserted);
        consolidated.addAll(inferred);
        target.setRelatedMatch(consolidated);
        final Term original = new Term();
        original.setRelatedMatch(new HashSet<>(asserted));
        original.setInverseRelatedMatch(new HashSet<>(inferred));
        sut.differentiateRelatedMatchTerms(target, original);
        assertEquals(asserted, target.getRelatedMatch());
        assertEquals(inferred, target.getInverseRelatedMatch());
    }

    @Test
    void differentiateRelatedMatchDoesNothingWhenOriginalInferredAreNotPresent() {
        final Term target = Generator.generateTermWithId();
        final Set<TermInfo> asserted = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId())).collect(Collectors.toSet());
        target.setRelatedMatch(asserted);
        final Term original = new Term();
        sut.differentiateRelatedMatchTerms(target, original);
        assertEquals(asserted, target.getRelatedMatch());
        assertThat(target.getInverseRelatedMatch(), anyOf(emptyCollectionOf(TermInfo.class), nullValue()));
    }


    @Test
    void differentiateExactMatchHandlesMovesTermsKnownToBeInferredInOriginalToInferredInTarget() {
        final Term target = Generator.generateTermWithId();
        final Set<TermInfo> asserted = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId())).collect(Collectors.toSet());
        final Set<TermInfo> inferred = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId())).collect(Collectors.toSet());
        final Set<TermInfo> consolidated = new HashSet<>(asserted);
        consolidated.addAll(inferred);
        target.setExactMatchTerms(consolidated);
        final Term original = new Term();
        original.setExactMatchTerms(new HashSet<>(asserted));
        original.setInverseExactMatchTerms(new HashSet<>(inferred));
        sut.differentiateExactMatchTerms(target, original);
        assertEquals(asserted, target.getExactMatchTerms());
        assertEquals(inferred, target.getInverseExactMatchTerms());
    }

    @Test
    void differentiateExactMatchDoesNothingWhenOriginalInferredAreNotPresent() {
        final Term target = Generator.generateTermWithId();
        final Set<TermInfo> asserted = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId())).collect(Collectors.toSet());
        target.setExactMatchTerms(asserted);
        final Term original = new Term();
        sut.differentiateExactMatchTerms(target, original);
        assertEquals(asserted, target.getExactMatchTerms());
        assertThat(target.getInverseExactMatchTerms(), anyOf(emptyCollectionOf(TermInfo.class), nullValue()));
    }
}