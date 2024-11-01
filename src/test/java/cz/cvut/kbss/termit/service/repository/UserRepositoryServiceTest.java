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
package cz.cvut.kbss.termit.service.repository;

import java.net.URI;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.persistence.dao.UserAccountDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class UserRepositoryServiceTest {

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Mock
    private UserAccountDao userAccountDao;

    @Spy
    private Configuration configuration = new Configuration();

    @Spy
    private IdentifierResolver identifierResolver = new IdentifierResolver(configuration);

    @Spy
    private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @InjectMocks
    private UserRepositoryService sut;

    @Test
    void existsByUsernameReturnsTrueForExistingUsername() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        when(userAccountDao.exists(user.getUsername())).thenReturn(true);

        assertTrue(sut.exists(user.getUsername()));
        verify(userAccountDao).exists(user.getUsername());
    }

    @Test
    void persistGeneratesIdentifierForUser() {
        final UserAccount user = Generator.generateUserAccount();
        user.setPassword("12345");
        user.setUri(null);
        configuration.getNamespace().setUser(Vocabulary.s_c_uzivatel_termitu + "/");
        sut.persist(user);
        assertNotNull(user.getUri());

        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountDao).persist(captor.capture());
        assertEquals(user, captor.getValue());
    }

    @Test
    void persistEncodesUserPassword() {
        final UserAccount user = Generator.generateUserAccount();
        final String plainPassword = "12345";
        user.setPassword(plainPassword);

        sut.persist(user);
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountDao).persist(captor.capture());
        assertTrue(passwordEncoder.matches(plainPassword, captor.getValue().getPassword()));
    }

    @Test
    void updateEncodesPasswordWhenItWasChanged() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        when(userAccountDao.find(user.getUri())).thenReturn(Optional.of(user));
        doAnswer(arg -> arg.getArgument(0)).when(userAccountDao).update(any());
        Environment.setCurrentUser(user);
        final String plainPassword = "updatedPassword01";
        user.setPassword(plainPassword);

        sut.update(user);
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountDao).update(captor.capture());
        assertTrue(passwordEncoder.matches(plainPassword, captor.getValue().getPassword()));
    }

    @Test
    void updateRetainsOriginalPasswordWhenItDoesNotChange() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        final String plainPassword = user.getPassword();
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        when(userAccountDao.find(user.getUri())).thenReturn(Optional.of(user));
        doAnswer(arg -> arg.getArgument(0)).when(userAccountDao).update(any());
        Environment.setCurrentUser(user);
        final UserAccount update = new UserAccount();
        update.setUri(user.getUri());
        update.setFirstName(user.getFirstName());
        update.setUsername(user.getUsername());
        update.setPassword(null); // Simulate instance being loaded from repo
        final String newLastName = "newLastName";
        update.setLastName(newLastName);

        sut.update(update);
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountDao).update(captor.capture());
        assertTrue(passwordEncoder.matches(plainPassword, captor.getValue().getPassword()));
        assertEquals(newLastName, captor.getValue().getLastName());
    }

    @Test
    void postLoadErasesPasswordFromInstance() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        when(userAccountDao.find(user.getUri())).thenReturn(Optional.of(user));

        final Optional<UserAccount> result = sut.find(user.getUri());
        assertTrue(result.isPresent());
        assertNull(result.get().getPassword());
    }

    @Test
    void updateThrowsValidationExceptionWhenUpdatedInstanceIsMissingValues() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        when(userAccountDao.find(user.getUri())).thenReturn(Optional.of(user));
        Environment.setCurrentUser(user);

        user.setUsername(null);
        user.setPassword(null); // Simulate instance being loaded from repo
        final ValidationException ex = assertThrows(ValidationException.class, () -> sut.update(user));
        assertThat(ex.getMessage(), containsString("username"));
    }

    @Test
    void persistDoesNotGenerateUriIfItIsAlreadyPresent() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        final URI originalUri = user.getUri();
        sut.persist(user);

        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountDao).persist(captor.capture());
        assertEquals(originalUri, captor.getValue().getUri());
    }
}
