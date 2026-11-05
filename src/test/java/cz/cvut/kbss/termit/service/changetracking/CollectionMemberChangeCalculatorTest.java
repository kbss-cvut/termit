package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.jopa.utils.ReflectionUtils;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AttributeChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.OrderedCollectionMembersUpdateChangeRecord;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class CollectionMemberChangeCalculatorTest {
    private final CollectionMemberChangeCalculator sut = new CollectionMemberChangeCalculator();
    private OrderedTermCollection original;
    private OrderedTermCollection changed;

    @BeforeEach
    void setUp() {
        Vocabulary vocabulary = Generator.generateVocabularyWithId();
        List<Term> members = Generator.generateTermsWithIds(5);
        original = Generator.generateOrderedTermCollection(vocabulary, members);
        changed = Generator.generateOrderedTermCollection(vocabulary, members);
    }

    @Test
    void calculateChangesReturnsRecordForOrderedCollection() {
        Collections.swap(changed.getMembers(), 1,4);

        Collection<AttributeChangeRecord<?>> records = sut.calculateChanges(changed, original);
        assertEquals(1, records.size());
        OrderedCollectionMembersUpdateChangeRecord record = assertInstanceOf(OrderedCollectionMembersUpdateChangeRecord.class, records.iterator().next());

        assertEquals(changed.getMembers(), record.getNewValue());
        assertNotSame(changed.getMembers(), record.getNewValue());

        assertEquals(original.getMembers(), record.getOriginalValue());
        assertNotSame(original.getMembers(), record.getOriginalValue());
    }


    @ParameterizedTest
    @ValueSource(classes = {Term.class, Vocabulary.class, TermCollection.class})
    void calculateChangesDoesNothingForNonOrderedCollectionAssets(Class<? extends Asset<?>> assetClass) {
        Asset<?> original = ReflectionUtils.instantiateUsingDefaultConstructor(assetClass);
        Asset<?> changed = ReflectionUtils.instantiateUsingDefaultConstructor(assetClass);
        assertNotNull(original);
        assertNotNull(changed);

        Collection<AttributeChangeRecord<?>> records = sut.calculateChanges(changed, original);
        assertThat(records).isEmpty();
    }
}
