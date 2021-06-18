package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MetamodelBasedChangeCalculatorTest extends BaseServiceTestRunner {

    @Autowired
    private MetamodelBasedChangeCalculator sut;

    @Test
    void calculateChangesDiscoversChangeInSingularLiteralAttribute() {
        final Vocabulary original = Generator.generateVocabularyWithId();
        original.getModel().setUri(Generator.generateUri());
        original.getGlossary().setUri(Generator.generateUri());
        final Vocabulary changed = cloneOf(original);
        changed.setLabel("Updated label");

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getUri(), record.getChangedEntity());
        assertEquals(URI.create(DC.Terms.TITLE), record.getChangedAttribute());
    }

    private static Vocabulary cloneOf(Vocabulary original) {
        final Vocabulary clone = new Vocabulary();
        clone.setUri(original.getUri());
        clone.setDescription(original.getDescription());
        clone.setModel(original.getModel());
        clone.setGlossary(original.getGlossary());
        clone.setLabel(original.getLabel());
        return clone;
    }

    @Test
    void calculateChangesDiscoversInSingularReferenceAttribute() {
        // Note: This does not normally happen, it simulates possible changes in model when assets would have singular references to other objects
        final Vocabulary original = Generator.generateVocabularyWithId();
        original.getModel().setUri(Generator.generateUri());
        original.getGlossary().setUri(Generator.generateUri());
        final Vocabulary changed = cloneOf(original);
        changed.setGlossary(new Glossary());
        changed.getGlossary().setUri(Generator.generateUri());

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getUri(), record.getChangedEntity());
        assertEquals(URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_glosar), record.getChangedAttribute());
    }

    @Test
    void calculateChangesDiscoversChangeInPluralLiteralAttribute() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        changed.setSources(IntStream.range(0, 5).mapToObj(i -> "http://source" + i).collect(Collectors.toSet()));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getUri(), record.getChangedEntity());
        assertEquals(URI.create(DC.Terms.SOURCE), record.getChangedAttribute());
    }

    static Term cloneOf(Term original) {
        final Term clone = new Term();
        clone.setUri(original.getUri());
        clone.setLabel(new MultilingualString(original.getLabel().getValue()));
        clone.setAltLabels(original.getAltLabels());
        clone.setHiddenLabels(original.getHiddenLabels());
        clone.setDefinition(new MultilingualString(original.getDefinition().getValue()));
        clone.setDescription(original.getDescription());
        clone.setVocabulary(original.getVocabulary());
        clone.setGlossary(original.getGlossary());
        return clone;
    }

    @Test
    void calculateChangesDiscoversChangesInPluralReferenceAttribute() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        original.setParentTerms(Collections.singleton(Generator.generateTermWithId()));
        changed.setParentTerms(Collections.singleton(Generator.generateTermWithId()));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getUri(), record.getChangedEntity());
        assertEquals(URI.create(SKOS.BROADER), record.getChangedAttribute());
    }

    @Test
    void calculateChangesDiscoversChangesInSingularIdentifierBasedReferenceAttribute() {
        final Term original = Generator.generateTermWithId();
        original.setGlossary(Generator.generateUri());
        final Term changed = cloneOf(original);
        changed.setGlossary(Generator.generateUri());
        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getUri(), record.getChangedEntity());
        assertEquals(URI.create(SKOS.IN_SCHEME), record.getChangedAttribute());
    }

    @Test
    void calculateChangesDiscoversChangesInPluralIdentifierBasedReferenceAttribute() {
        final Vocabulary original = Generator.generateVocabularyWithId();
        final Vocabulary changed = cloneOf(original);
        original.setImportedVocabularies(
                IntStream.range(0, 5).mapToObj(i -> Generator.generateUri()).collect(Collectors.toSet()));
        changed.setImportedVocabularies(new HashSet<>(original.getImportedVocabularies()));
        changed.getImportedVocabularies().add(Generator.generateUri());

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getUri(), record.getChangedEntity());
        assertEquals(URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik),
                record.getChangedAttribute());
    }

    @Test
    void calculateChangesSkipsInferredAttributes() {
        final Document original = Generator.generateDocumentWithId();
        final Document changed = new Document();
        changed.setUri(original.getUri());
        changed.setLabel(original.getLabel());
        changed.setDescription(original.getDescription());
        original.setVocabulary(Generator.generateUri());

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertTrue(result.isEmpty());
    }

    @Test
    void calculateChangesReturnsEmptyCollectionForIdenticalOriginalAndUpdate() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertTrue(result.isEmpty());
    }

    @Test
    void calculateChangesHandlesChangeToNullInReference() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        original.setParentTerms(Collections.singleton(Generator.generateTermWithId()));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getUri(), record.getChangedEntity());
        assertEquals(URI.create(SKOS.BROADER), record.getChangedAttribute());
    }

    @Test
    void calculateChangesDiscoversChangeInTypes() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        original.setTypes(Collections.singleton(Generator.generateUri().toString()));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getUri(), record.getChangedEntity());
        assertEquals(URI.create(RDF.TYPE), record.getChangedAttribute());
    }

    @Test
    void calculateChangesDiscoversChangeInUnmappedProperties() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        final URI property = Generator.generateUri();
        original.setProperties(Collections.singletonMap(property.toString(), Collections.singleton("Test")));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getUri(), record.getChangedEntity());
        assertEquals(property, record.getChangedAttribute());
    }

    @Test
    void calculateChangesDiscoversChangeInUpdatedUnmappedProperties() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        final URI property = Generator.generateUri();
        changed.setProperties(Collections.singletonMap(property.toString(), Collections.singleton("Test")));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getUri(), record.getChangedEntity());
        assertEquals(property, record.getChangedAttribute());
    }

    @Test
    void calculateChangesDiscoversChangeInValuesOfSingleUnmappedProperty() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        final URI property = Generator.generateUri();
        original.setProperties(Collections.singletonMap(property.toString(), Collections.singleton("Test")));
        changed.setProperties(Collections.singletonMap(property.toString(), Collections.singleton("Different test")));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getUri(), record.getChangedEntity());
        assertEquals(property, record.getChangedAttribute());
    }

    @Test
    void calculateChangesDiscoversMultipleChangesAtOnce() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        original.setParentTerms(Collections.singleton(Generator.generateTermWithId()));
        changed.setTypes(Collections.singleton(Generator.generateUri().toString()));
        changed.getLabel().set(Environment.LANGUAGE, "Updated label");

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(r -> r.getChangedAttribute().equals(URI.create(RDF.TYPE))));
        assertTrue(result.stream().anyMatch(r -> r.getChangedAttribute().equals(URI.create(SKOS.BROADER))));
        assertTrue(result.stream().anyMatch(r -> r.getChangedAttribute().equals(URI.create(SKOS.PREF_LABEL))));
    }

    @Test
    void calculateChangesReturnsChangeRecordWithOriginalAndNewValueOfSingularLiteralAttribute() {
        final Vocabulary original = Generator.generateVocabularyWithId();
        original.getModel().setUri(Generator.generateUri());
        original.getGlossary().setUri(Generator.generateUri());
        final Vocabulary changed = cloneOf(original);
        changed.setLabel("Updated label");

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(Collections.singleton(original.getLabel()), record.getOriginalValue());
        assertEquals(Collections.singleton(changed.getLabel()), record.getNewValue());
    }

    @Test
    void calculateChangesReturnsChangeRecordWithOriginalAndNewValueOfSingularReferenceAttribute() {
        // Note: This does not normally happen, it simulates possible changes in model when assets would have singular references to other objects
        final Vocabulary original = Generator.generateVocabularyWithId();
        original.getModel().setUri(Generator.generateUri());
        original.getGlossary().setUri(Generator.generateUri());
        final Vocabulary changed = cloneOf(original);
        changed.setGlossary(new Glossary());
        changed.getGlossary().setUri(Generator.generateUri());

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(Collections.singleton(original.getGlossary().getUri()), record.getOriginalValue());
        assertEquals(Collections.singleton(changed.getGlossary().getUri()), record.getNewValue());
    }

    @Test
    void calculateChangesReturnsChangeRecordWithOriginalAndNewValueOfPluralLiteralAttribute() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        changed.setSources(IntStream.range(0, 5).mapToObj(i -> "http://source" + i).collect(Collectors.toSet()));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertNull(record.getOriginalValue());
        assertEquals(changed.getSources(), record.getNewValue());
    }

    @Test
    void calculateChangesReturnsChangeRecordWithOriginalAndNewValueOfPluralReferenceAttribute() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        original.setParentTerms(Collections.singleton(Generator.generateTermWithId()));
        changed.setParentTerms(Collections.singleton(Generator.generateTermWithId()));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getParentTerms().stream().map(Term::getUri).collect(Collectors.toSet()),
                record.getOriginalValue());
        assertEquals(changed.getParentTerms().stream().map(Term::getUri).collect(Collectors.toSet()),
                record.getNewValue());
    }

    @Test
    void calculateChangesReturnsChangeRecordWithOriginalAndNewValueOfSingularIdentifierBasedReferenceAttribute() {
        final Term original = Generator.generateTermWithId();
        original.setGlossary(Generator.generateUri());
        final Term changed = cloneOf(original);
        changed.setGlossary(Generator.generateUri());

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(Collections.singleton(original.getGlossary()), record.getOriginalValue());
        assertEquals(Collections.singleton(changed.getGlossary()), record.getNewValue());
    }

    @Test
    void calculateChangesReturnsChangeRecordWithOriginalAndNewValueOfPluralIdentifierBasedReferenceAttribute() {
        final Vocabulary original = Generator.generateVocabularyWithId();
        final Vocabulary changed = cloneOf(original);
        original.setImportedVocabularies(
                IntStream.range(0, 5).mapToObj(i -> Generator.generateUri()).collect(Collectors.toSet()));
        changed.setImportedVocabularies(new HashSet<>(original.getImportedVocabularies()));
        changed.getImportedVocabularies().add(Generator.generateUri());

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getImportedVocabularies(), record.getOriginalValue());
        assertEquals(changed.getImportedVocabularies(), record.getNewValue());
    }

    @Test
    void calculateChangesReturnsChangeRecordWithOriginalAndNewValueOfTypes() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        original.setTypes(Collections.singleton(Generator.generateUri().toString()));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getTypes().stream().map(URI::create).collect(Collectors.toSet()),
                record.getOriginalValue());
        assertNull(record.getNewValue());
    }

    @Test
    void calculateChangesReturnsChangeRecordWithOriginalAndNewValueOfUnmappedProperty() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        final URI property = Generator.generateUri();
        original.setProperties(Collections.singletonMap(property.toString(), Collections.singleton("Test")));
        changed.setProperties(Collections.singletonMap(property.toString(), Collections.singleton("Different test")));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getProperties().get(property.toString()), record.getOriginalValue());
        assertEquals(changed.getProperties().get(property.toString()), record.getNewValue());
    }

    @Test
    void calculateChangesReturnsChangeRecordWithEmptyOriginalAndAddedNewValueOfUnmappedProperty() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        final URI property = Generator.generateUri();
        changed.setProperties(Collections.singletonMap(property.toString(), Collections.singleton("Different test")));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertNull(record.getOriginalValue());
        assertEquals(changed.getProperties().get(property.toString()), record.getNewValue());
    }

    @Test
    void calculateChangesReturnsChangeRecordWithEmptyOriginalAndAddedNewValueOfReferenceAttribute() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        changed.setParentTerms(Collections.singleton(Generator.generateTermWithId()));

        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertNull(record.getOriginalValue());
        assertEquals(changed.getParentTerms().stream().map(Term::getUri).collect(Collectors.toSet()),
                record.getNewValue());
    }

    @Test
    void calculateChangesDoesNotRegisterChangeInTypesWhenOriginalIsEmptyAndUpdateIsNull() {
        final Term original = Generator.generateTermWithId();
        original.setTypes(Collections.emptySet());
        final Term changed = cloneOf(original);
        changed.setTypes(null);
        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertTrue(result.isEmpty());
    }

    @Test
    void calculateChangesRegistersAddedTranslationInMultilingualTermLabel() {
        final Term original = Generator.generateTermWithId();
        final Term changed = cloneOf(original);
        changed.getLabel().set("cs", "Testovací pojem");
        final Collection<UpdateChangeRecord> result = sut.calculateChanges(changed, original);
        assertEquals(1, result.size());
        final UpdateChangeRecord record = result.iterator().next();
        assertEquals(original.getLabel(), record.getOriginalValue().iterator().next());
        assertEquals(changed.getLabel(), record.getNewValue().iterator().next());
    }
}
