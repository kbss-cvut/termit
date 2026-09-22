package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRollbackDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetamodelRollbackValidatorTest {

    @Mock
    private ChangeRollbackDao changeRollbackDao;

    @Mock
    private Attribute<?, ?> attribute;

    @InjectMocks
    private MetamodelRollbackValidator sut;

    private static Stream<Set<Object>> primitiveOriginalValues() {
        return Stream.of(
                Set.of("original", "another original"),
                Set.of(1, 2),
                Set.of(true, false),
                Set.of(MultilingualString.create("str value", "en")),
                Set.of(new Date()),
                Set.of(Instant.now(), Instant.now().plusSeconds(100))
        );
    }

    @ParameterizedTest
    @MethodSource("primitiveOriginalValues")
    void canRollbackReturnsTrueWhenAllChangedValuesArePrimitive(Set<Object> originalValues) {
        final UpdateChangeRecord record = recordForChange(originalValues, Set.of("updated", "another updated"));

        assertTrue(sut.canRollback(record, Term.class));
        verifyNoInteractions(changeRollbackDao);
    }

    @Test
    void canRollbackSupportsPrimitiveValueAddedToPreviouslyNullAttribute() {
        final UpdateChangeRecord record = recordForChange(null, Set.of("added"));

        assertTrue(sut.canRollback(record, Term.class));
        verifyNoInteractions(changeRollbackDao);
    }

    @Test
    void canRollbackSupportsPrimitiveValueAddedToPreviouslyEmptyAttribute() {
        final UpdateChangeRecord record = recordForChange(Set.of(), Set.of("added"));

        assertTrue(sut.canRollback(record, Term.class));
        verifyNoInteractions(changeRollbackDao);
    }

    @Test
    void canRollbackReturnsFalseWhenChangedAttributeCannotBeResolved() {
        final UpdateChangeRecord record = recordForChange(Set.of(Generator.generateUri()), Set.of(Generator.generateUri()));
        when(changeRollbackDao.resolveClassAttribute(Term.class, record)).thenReturn(Optional.empty());

        assertFalse(sut.canRollback(record, Term.class));
    }

    @Test
    void canRollbackReturnsTrueWhenAllOriginalEntityReferencesExist() {
        final URI firstReference = Generator.generateUri();
        final URI secondReference = Generator.generateUri();
        final UpdateChangeRecord record = recordForChange(Set.of(firstReference, secondReference),
                                                 Set.of(Generator.generateUri()));
        mockReferenceAttribute(record);
        when(changeRollbackDao.entityExists(firstReference)).thenReturn(true);
        when(changeRollbackDao.entityExists(secondReference)).thenReturn(true);

        assertTrue(sut.canRollback(record, Term.class));
    }

    @Test
    void canRollbackReturnsFalseWhenOriginalEntityReferenceNoLongerExists() {
        final URI missingReference = Generator.generateUri();
        final URI existingReference = Generator.generateUri();
        final UpdateChangeRecord record = recordForChange(Set.of(existingReference, missingReference), Set.of(Generator.generateUri()));
        mockReferenceAttribute(record);
        lenient().when(changeRollbackDao.entityExists(existingReference)).thenReturn(true);
        when(changeRollbackDao.entityExists(missingReference)).thenReturn(false);

        assertFalse(sut.canRollback(record, Term.class));
    }

    @Test
    void canRollbackRestoresRemovedMultilingualLabel() {
        final UpdateChangeRecord record = recordForChange(Set.of(MultilingualString.create("Original", "en")), Set.of());

        assertTrue(sut.canRollback(record, Term.class));
        verifyNoInteractions(changeRollbackDao);
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void canRollbackChecksForEntityReferenceExistence(boolean entityExists) {
        final URI reference = Generator.generateUri();
        final URI existingReference = Generator.generateUri();
        final UpdateChangeRecord record = recordForChange(Set.of(existingReference, reference), null);

        doReturn(Optional.of(attribute)).when(changeRollbackDao).resolveClassAttribute(Vocabulary.class, record);
        doReturn(URI.class).when(attribute).getValueJavaType();

        lenient().when(changeRollbackDao.entityExists(existingReference)).thenReturn(true);
        when(changeRollbackDao.entityExists(reference)).thenReturn(entityExists);

        assertEquals(entityExists, sut.canRollback(record, Vocabulary.class));
        verify(changeRollbackDao).entityExists(reference);
    }

    private void mockReferenceAttribute(UpdateChangeRecord record) {
        doReturn(Optional.of(attribute)).when(changeRollbackDao).resolveClassAttribute(Term.class, record);
        doReturn(URI.class).when(attribute).getValueJavaType();
    }

    private static UpdateChangeRecord recordForChange(Set<Object> originalValue, Set<Object> newValue) {
        final UpdateChangeRecord record = new UpdateChangeRecord();
        record.setChangedAttribute(Generator.generateUri());
        record.setOriginalValue(originalValue);
        record.setNewValue(newValue);
        return record;
    }
}
