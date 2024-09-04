/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.dto.mapper;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.acl.AccessControlRecordDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.acl.RoleAccessControlRecord;
import cz.cvut.kbss.termit.model.acl.UserAccessControlRecord;
import cz.cvut.kbss.termit.model.acl.UserGroupAccessControlRecord;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
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
        final UserRole role = new UserRole(URI.create(Vocabulary.s_c_omezeny_uzivatel_termitu));
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
        final UserRole role = new UserRole(URI.create(Vocabulary.s_c_omezeny_uzivatel_termitu));
        role.setLabel(MultilingualString.create("Reader", Environment.LANGUAGE));
        final RoleAccessControlRecord rThree = new RoleAccessControlRecord(AccessLevel.WRITE, role);
        rThree.setUri(Generator.generateUri());
        return Stream.of(
                Arguments.of(rOne, Vocabulary.s_c_uzivatel_termitu),
                Arguments.of(rTwo, Vocabulary.s_c_sioc_Usergroup),
                Arguments.of(rThree, Vocabulary.s_c_uzivatelska_role)
        );
    }

    @Test
    void accessControlRecordToDtoCopiesTypesOfUserHolder() {
        final UserAccessControlRecord record = new UserAccessControlRecord(AccessLevel.SECURITY,
                                                                           Generator.generateUserWithId());
        record.getHolder().addType(cz.cvut.kbss.termit.security.model.UserRole.RESTRICTED_USER.getType());
        final AccessControlRecordDto result = sut.accessControlRecordToDto(record);
        assertThat(result.getHolder().getTypes(), hasItems(record.getHolder().getTypes().toArray(new String[]{})));
    }
}
