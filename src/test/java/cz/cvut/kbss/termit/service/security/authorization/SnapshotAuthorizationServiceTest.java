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
package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotAuthorizationServiceTest {

    @Mock
    private VocabularyAuthorizationService vocabularyAuthorizationService;

    @Spy
    private SecurityUtils securityUtils = new SecurityUtils(null, new BCryptPasswordEncoder(), null,
                                                            new Configuration());

    @InjectMocks
    private SnapshotAuthorizationService sut;

    @Test
    void canRemoveChecksIfUserCanRemoveSnapshotOfTargetVocabularyWithVocabularyAuthorizationService() {
        final Vocabulary target = Generator.generateVocabularyWithId();
        final Snapshot snapshot = Generator.generateSnapshot(target);
        when(vocabularyAuthorizationService.canRemoveSnapshot(target)).thenReturn(true);

        assertTrue(sut.canRemove(snapshot));
        verify(vocabularyAuthorizationService).canRemoveSnapshot(target);
    }

    @ParameterizedTest
    @MethodSource("removalOfNonVocabularySnapshotRights")
    void canRemoveRequiresUserToBeAtLeastEditorForRemovalOfSnapshotOfDifferentAssetThanVocabulary(boolean expected,
                                                                                                  UserRole role) {
        final UserAccount currentUser = Generator.generateUserAccount();
        currentUser.addType(role.getType());
        Environment.setCurrentUser(currentUser);
        final Term target = Generator.generateTermWithId();
        final Snapshot snapshot = Generator.generateSnapshot(target);

        assertEquals(expected, sut.canRemove(snapshot));
        verify(vocabularyAuthorizationService, never()).canRemoveSnapshot(any());
    }

    static Stream<Arguments> removalOfNonVocabularySnapshotRights() {
        return Stream.of(
                Arguments.of(false, UserRole.RESTRICTED_USER),
                Arguments.of(true, UserRole.FULL_USER),
                Arguments.of(true, UserRole.ADMIN)
        );
    }
}
