package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.JwtException;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.JwtUtils;
import cz.cvut.kbss.termit.service.repository.PersonalAccessTokenRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PersonalAccessTokenServiceTest {
    private PersonalAccessTokenService sut;

    @Mock
    private PersonalAccessTokenRepositoryService repositoryService;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private JwtUtils jwtUtils;

    private UserAccount currentUser;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        sut = new PersonalAccessTokenService(repositoryService, securityUtils, jwtUtils, validator);
        currentUser = Generator.generateUserAccount();
    }

    @Test
    public void findAllForCurrentUserFindsTokensForCurrentUser() {
        when(securityUtils.getCurrentUser()).thenReturn(currentUser);
        sut.findAllForCurrentUser();
        verify(repositoryService, atLeastOnce()).findAllByUserAccount(currentUser);
    }

    @Test
    public void ensureTokenValidAcceptsValidTokenWithoutExpiration() {
        PersonalAccessToken token = Generator.generatePersonalAccessToken(currentUser);
        PersonalAccessToken result = sut.ensureTokenValid(token);
        assertEquals(token, result);
    }

    @Test
    public void ensureTokenValidAcceptsValidTokenWithExpirationInFuture() {
        PersonalAccessToken token = Generator.generatePersonalAccessToken(currentUser);
        token.setExpirationDate(LocalDate.now().plusDays(2));
        PersonalAccessToken result = sut.ensureTokenValid(token);
        assertEquals(token, result);
    }

    @Test
    public void ensureTokenValidAcceptsValidTokenWithExpirationToday() {
        PersonalAccessToken token = Generator.generatePersonalAccessToken(currentUser);
        token.setExpirationDate(LocalDate.now());
        PersonalAccessToken result = sut.ensureTokenValid(token);
        assertEquals(token, result);
    }

    @Test
    public void ensureTokenValidThrowsForExpiredToken() {
        PersonalAccessToken token = Generator.generatePersonalAccessToken(currentUser);
        token.setExpirationDate(LocalDate.now().minusDays(1));
        assertThrows(JwtException.class, () -> sut.ensureTokenValid(token));
    }

    @Test
    public void ensureTokenValidThrowsForTokenWithoutOwner() {
        PersonalAccessToken token = Generator.generatePersonalAccessToken(currentUser);
        token.setOwner(null);
        assertThrows(JwtException.class, () -> sut.ensureTokenValid(token));
    }

    @Test
    public void findValidReturnsUserAccountForValidToken() {
        PersonalAccessToken token = Generator.generatePersonalAccessToken(currentUser);
        when(repositoryService.find(token.getUri())).thenReturn(Optional.of(token));

        final PersonalAccessToken result = sut.findValid(token.getUri());
        assertEquals(token, result);
    }

    @Test
    public void findValidThrowsForExpiredToken() {
        PersonalAccessToken token = Generator.generatePersonalAccessToken(currentUser);
        when(repositoryService.find(token.getUri())).thenReturn(Optional.of(token));
        token.setExpirationDate(LocalDate.now().minusDays(1));

        assertThrows(JwtException.class, () -> sut.findValid(token.getUri()));
    }

    @Test
    public void createCreatesTokenForCurrentUser() {
        when(securityUtils.getCurrentUser()).thenReturn(currentUser);

        sut.create(null);

        final ArgumentCaptor<PersonalAccessToken> tokenCaptor = ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(repositoryService, atLeastOnce()).persist(tokenCaptor.capture());
        final PersonalAccessToken createdToken = tokenCaptor.getValue();
        assertEquals(currentUser, createdToken.getOwner());
        assertNull(createdToken.getExpirationDate());
        assertNull(createdToken.getLastUsed());
    }

    @ParameterizedTest
    @CsvSource(", 2025-12-17") // null and normal value
    public void createCreatesTokenWithGivenExpirationDate(LocalDate expiration) {
        when(securityUtils.getCurrentUser()).thenReturn(currentUser);

        sut.create(expiration);
        final ArgumentCaptor<PersonalAccessToken> tokenCaptor = ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(repositoryService, atLeastOnce()).persist(tokenCaptor.capture());

        PersonalAccessToken token = tokenCaptor.getValue();
        assertEquals(expiration, token.getExpirationDate());
        assertNull(token.getLastUsed());
    }

    @Test
    public void deleteThrowsForNonCurrentUserOwnedToken() {
        PersonalAccessToken token = Generator.generatePersonalAccessToken(Generator.generateUserAccount());

        when(securityUtils.getCurrentUser()).thenReturn(currentUser);
        when(repositoryService.findRequired(token.getUri())).thenReturn(token);

        assertThrows(AuthorizationException.class, () -> sut.delete(token.getUri()));
    }

    @Test
    public void deleteCallsRepositoryRemoveForTokenOwnedByCurrentUser() {
        PersonalAccessToken token = Generator.generatePersonalAccessToken(currentUser);

        when(securityUtils.getCurrentUser()).thenReturn(currentUser);
        when(repositoryService.findRequired(token.getUri())).thenReturn(token);

        sut.delete(token.getUri());
        verify(repositoryService, atLeastOnce()).remove(token);
    }
}
