package cz.cvut.kbss.termit.dto.mapper;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.acl.AccessControlRecordDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.model.acl.*;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DtoMapperTest {

    private final DtoMapper sut = Environment.getDtoMapper();

    @ParameterizedTest
    @MethodSource("recordsGenerator")
    void accessControlRecordToDtoSetsDtoTypeBasedOnRecordMappedType(AccessControlRecord<?> record, String type) {
        final AccessControlRecordDto result = sut.accessControlRecordToDto(record);
        assertThat(result.getTypes(), hasItem(type));
    }

    static Stream<Arguments> recordsGenerator() {
        final UserAccessControlRecord rOne = new UserAccessControlRecord(AccessLevel.SECURITY,
                                                                         Generator.generateUserWithId());
        rOne.setUri(Generator.generateUri());
        final UserGroupAccessControlRecord rTwo = new UserGroupAccessControlRecord(AccessLevel.READ,
                                                                                   Generator.generateUserGroup());
        rTwo.setUri(Generator.generateUri());
        final UserRole role = new UserRole();
        role.setUri(URI.create(Vocabulary.s_c_omezeny_uzivatel_termitu));
        role.setLabel(MultilingualString.create("Reader", Environment.LANGUAGE));
        final RoleAccessControlRecord rThree = new RoleAccessControlRecord(AccessLevel.WRITE, role);
        rThree.setUri(Generator.generateUri());
        return Stream.of(
                Arguments.of(rOne, Vocabulary.s_c_zaznam_rizeni_pristupu_uzivatele),
                Arguments.of(rTwo, Vocabulary.s_c_zaznam_rizeni_pristupu_uzivatelske_skupiny),
                Arguments.of(rThree, Vocabulary.s_c_zaznam_rizeni_pristupu_uzivatelske_role)
        );
    }

    @ParameterizedTest
    @MethodSource("holdersGenerator")
    void accessControlRecordToDtoTransformsHolderToCommonDtoType(AccessControlRecord<?> record, String holderType) {
        final AccessControlRecordDto result = sut.accessControlRecordToDto(record);
        assertNotNull(result.getHolder());
        assertEquals(record.getHolder().getUri(), result.getHolder().getUri());
        assertThat(result.getHolder().getTypes(), hasItem(holderType));
    }

    static Stream<Arguments> holdersGenerator() {
        final UserAccessControlRecord rOne = new UserAccessControlRecord(AccessLevel.SECURITY,
                                                                         Generator.generateUserWithId());
        rOne.setUri(Generator.generateUri());
        final UserGroupAccessControlRecord rTwo = new UserGroupAccessControlRecord(AccessLevel.READ,
                                                                                   Generator.generateUserGroup());
        rTwo.setUri(Generator.generateUri());
        final UserRole role = new UserRole();
        role.setUri(URI.create(Vocabulary.s_c_omezeny_uzivatel_termitu));
        role.setLabel(MultilingualString.create("Reader", Environment.LANGUAGE));
        final RoleAccessControlRecord rThree = new RoleAccessControlRecord(AccessLevel.WRITE, role);
        rThree.setUri(Generator.generateUri());
        return Stream.of(
                Arguments.of(rOne, Vocabulary.s_c_uzivatel_termitu),
                Arguments.of(rTwo, Vocabulary.s_c_Usergroup),
                Arguments.of(rThree, Vocabulary.s_c_uzivatelska_role)
        );
    }
}
