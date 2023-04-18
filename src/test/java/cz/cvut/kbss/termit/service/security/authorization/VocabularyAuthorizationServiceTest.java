package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.security.authorization.acl.AccessControlListBasedAuthorizationService;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyAuthorizationServiceTest {

    @Mock
    private EditableVocabularies editableVocabularies;

    @Mock
    private AccessControlListBasedAuthorizationService aclBasedAuthService;

    @InjectMocks
    private VocabularyAuthorizationService sut;

    private final UserAccount user = Generator.generateUserAccount();

    private final Vocabulary vocabulary = Generator.generateVocabularyWithId();

    @ParameterizedTest
    @MethodSource("getCanCreateResultAndParams")
    void canCreateRequiresAtLeastEditorUser(boolean expected, UserRole role) {
        user.addType(role.getType());
        Environment.setCurrentUser(user);
        assertEquals(expected, sut.canCreate());
    }

    static Stream<Arguments> getCanCreateResultAndParams() {
        return Stream.of(
                Arguments.of(false, UserRole.RESTRICTED_USER),
                Arguments.of(true, UserRole.FULL_USER),
                Arguments.of(true, UserRole.ADMIN)
        );
    }

    @Test
    void canReadChecksIfVocabularyIsInCurrentWorkspace() {
        Environment.setCurrentUser(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);
        when(aclBasedAuthService.canRead(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canRead(vocabulary));
        verify(editableVocabularies).isEditable(vocabulary);
    }

    @Test
    void canReadChecksIfCurrentUserHasAccessBasedOnAccessControlList() {
        Environment.setCurrentUser(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);
        when(aclBasedAuthService.canRead(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canRead(vocabulary));
        verify(aclBasedAuthService).canRead(user, vocabulary);
    }

    @Test
    void canModifyChecksIfVocabularyIsEditableInCurrentWorkspace() {
        Environment.setCurrentUser(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);
        when(aclBasedAuthService.canModify(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canModify(vocabulary));
        verify(editableVocabularies).isEditable(vocabulary);
    }

    @Test
    void canModifyChecksIfCurrentUserHasAccessBasedOnAccessControlList() {
        Environment.setCurrentUser(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);
        when(aclBasedAuthService.canModify(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canModify(vocabulary));
        verify(aclBasedAuthService).canModify(user, vocabulary);
    }

    @Test
    void canRemoveChecksIfVocabularyIsEditableInCurrentWorkspace() {
        Environment.setCurrentUser(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);
        when(aclBasedAuthService.canRemove(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canRemove(vocabulary));
        verify(editableVocabularies).isEditable(vocabulary);
    }

    @Test
    void canRemoveChecksIfCurrentUserHasAccessBasedOnAccessControlList() {
        Environment.setCurrentUser(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);
        when(aclBasedAuthService.canRemove(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canRemove(vocabulary));
        verify(aclBasedAuthService).canRemove(user, vocabulary);
    }
}
