package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.security.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotAuthorizationServiceTest {

    @Mock
    private VocabularyAuthorizationService vocabularyAuthorizationService;

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
