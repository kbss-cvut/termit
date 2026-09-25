package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.VocabularyDto;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Term_;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Vocabulary_;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeRollbackDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private ChangeRollbackDao sut;

    @Test
    void entityExistsReturnsTrueForExistingEntity() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary));

        assertTrue(sut.entityExists(vocabulary.getUri()));
    }

    @Test
    void entityExistsReturnsFalseForNonExistingEntity() {
        assertFalse(sut.entityExists(Generator.generateUri()));
    }

    @Test
    void resolveClassAttributeFindsMappedAttributeByIdentifier() {
        final UpdateChangeRecord record = recordForAttribute(Term_.definitionPropertyIRI.toURI());

        final Optional<Attribute<? super Term, ?>> result = sut.resolveClassAttribute(Term.class, record);

        assertTrue(result.isPresent());
        assertEquals(Term_.definition, result.get());
    }

    @Test
    void resolveClassAttributeReturnsEmptyForUnknownProperty() {
        final UpdateChangeRecord record = recordForAttribute(Generator.generateUri());
        assertTrue(sut.resolveClassAttribute(Term.class, record).isEmpty());
    }

    @Test
    void resolveClassAttributeThrowsForUnknownEntityClass() {
        final UpdateChangeRecord record = recordForAttribute(Term_.labelPropertyIRI.toURI());
        assertThrows(IllegalArgumentException.class, () -> sut.resolveClassAttribute(VocabularyDto.class, record));
    }

    @Test
    void rollbackClassAttributeRestoresSingularValue() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.setPrimaryLanguage("en");
        final String originalLanguage = "cs";

        sut.rollbackClassAttribute(Set.of(originalLanguage), vocabulary, Vocabulary_.primaryLanguage);

        assertEquals(originalLanguage, vocabulary.getPrimaryLanguage());
    }

    @Test
    void rollbackClassAttributeRestoresMultilingualValue() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.setLabel(MultilingualString.create("Slovník", "cs"));
        final MultilingualString originalLabel = MultilingualString.create("Vocabulary", "en");

        sut.rollbackClassAttribute(Set.of(originalLabel), vocabulary, Vocabulary_.label);

        assertEquals(originalLabel, vocabulary.getLabel());
    }

    @Test
    void rollbackClassAttributeSetsSingularValueToNullWhenOriginalValueWasNull() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        sut.rollbackClassAttribute(null, vocabulary, Vocabulary_.label);

        assertNull(vocabulary.getLabel());
    }

    @Test
    void rollbackClassAttributeSetsSingularValueToNullWhenOriginalValueWasEmpty() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        sut.rollbackClassAttribute(Set.of(), vocabulary, Vocabulary_.label);

        assertNull(vocabulary.getLabel());
    }

    @Test
    void rollbackClassAttributeRestoresSetCollectionValue() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.setImportedVocabularies(Set.of(Generator.generateUri()));
        final Set<Object> originalImported = Set.of(Generator.generateUri());

        sut.rollbackClassAttribute(originalImported, vocabulary, Vocabulary_.importedVocabularies);

        assertEquals(originalImported, vocabulary.getImportedVocabularies());
    }

    @Test
    void rollbackClassAttributeResolvesEntityReferences() {
        final Term term = Generator.generateTermWithId();
        term.setParentTerms(Set.of(Generator.generateTermInfoWithId()));

        final Set<TermInfo> originalParents = Set.of(Generator.generateTermInfoWithId(), Generator.generateTermInfoWithId());
        transactional(() -> {
            originalParents.forEach(em::persist);
        });

        final Set<Object> parentIdentifiers = originalParents.stream().map(HasIdentifier::getUri).collect(Collectors.toUnmodifiableSet());

        sut.rollbackClassAttribute(parentIdentifiers, term, Term_.parentTerms);

        assertEquals(originalParents, term.getParentTerms());
    }

    @Test
    void rollbackNativePropertyRestoresSingleOriginalValue() {
        final Term term = Generator.generateTermWithId();
        final URI property = Generator.generateUri();

        term.setProperties(new HashMap<>());
        term.getProperties().put(property.toString(), Set.of("custom term property"));

        final Set<Object> originalValues = Set.of("original");

        sut.rollbackNativeProperty(originalValues, term, property);

        assertEquals(originalValues, term.getProperties().get(property.toString()));
    }

    @Test
    void rollbackNativePropertyDoesNotModifyOtherNativeProperties() {
        final Term term = Generator.generateTermWithId();
        final URI anotherProperty = Generator.generateUri();
        final Set<Object> anotherPropertyValues = Set.of(new Object(), new Object());
        term.setProperties(new HashMap<>());
        term.getProperties().put(anotherProperty.toString(), anotherPropertyValues);

        final URI property = Generator.generateUri();
        final Set<Object> originalValues = Set.of("original");

        sut.rollbackNativeProperty(originalValues, term, property);

        assertEquals(originalValues, term.getProperties().get(property.toString()));
        assertEquals(anotherPropertyValues, term.getProperties().get(anotherProperty.toString()));
    }

    @Test
    void rollbackNativePropertyRemovesPropertyThatDidNotOriginallyExist() {
        final Term term = Generator.generateTermWithId();
        final URI property = Generator.generateUri();
        term.setProperties(new HashMap<>());
        term.getProperties().put(property.toString(), Set.of("added"));

        sut.rollbackNativeProperty(null, term, property);

        assertFalse(term.getProperties().containsKey(property.toString()));
    }

    @Test
    void rollbackNativePropertyRemovesPropertyWhenOriginalValueIsEmptySet() {
        final Term term = Generator.generateTermWithId();
        final URI property = Generator.generateUri();
        term.setProperties(new HashMap<>());
        term.getProperties().put(property.toString(), Set.of("added"));

        sut.rollbackNativeProperty(Set.of(), term, property);

        assertFalse(term.getProperties().containsKey(property.toString()));
    }

    @Test
    void rollbackNativePropertyRejectsAssetWithoutProperties() {
        final Document document = Generator.generateDocumentWithId();

        assertThrows(IllegalArgumentException.class,
                     () -> sut.rollbackNativeProperty(Set.of("original"), document, Generator.generateUri()));
    }

    private static UpdateChangeRecord recordForAttribute(URI changedAttribute) {
        final UpdateChangeRecord record = new UpdateChangeRecord();
        record.setChangedAttribute(changedAttribute);
        return record;
    }
}
