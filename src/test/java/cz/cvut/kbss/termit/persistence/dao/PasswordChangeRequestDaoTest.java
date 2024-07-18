package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordChangeRequestDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private PasswordChangeRequestDao sut;

    private String randomToken() {
        return UUID.randomUUID().toString();
    }

    @Test
    void findByTokenReturnsResultMatchingTheToken() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));

        final String TOKEN = randomToken();
        final PasswordChangeRequest passwordChangeRequest = new PasswordChangeRequest();
        passwordChangeRequest.setToken(TOKEN);
        passwordChangeRequest.setUserAccount(user);
        passwordChangeRequest.setCreatedAt(Instant.now());
        transactional(() -> em.persist(passwordChangeRequest));

        final Optional<PasswordChangeRequest> result = sut.findByToken(TOKEN);
        assertTrue(result.isPresent());
        assertEquals(TOKEN, result.get().getToken());
        assertEquals(user.getUri(), result.get().getUserAccount().getUri());
    }

    @Test
    void findByTokenReturnsEmptyOptionalWhenNoMatchingRequestIsFound() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));

        final String TOKEN = randomToken();
        final String ANOTHER_TOKEN = randomToken();

        final PasswordChangeRequest passwordChangeRequest = new PasswordChangeRequest();
        passwordChangeRequest.setToken(TOKEN);
        passwordChangeRequest.setUserAccount(user);
        passwordChangeRequest.setCreatedAt(Instant.now());
        transactional(() -> em.persist(passwordChangeRequest));

        final Optional<PasswordChangeRequest> result = sut.findByToken(ANOTHER_TOKEN);
        assertFalse(result.isPresent());
    }

    @Test
    void findByTokenReturnsSingleResultWhenMultipleArePresent() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));

        final String TOKEN = randomToken();
        final PasswordChangeRequest passwordChangeRequest = new PasswordChangeRequest();
        final PasswordChangeRequest secondPasswordChangeRequest = new PasswordChangeRequest();
        passwordChangeRequest.setToken(TOKEN);
        secondPasswordChangeRequest.setToken(TOKEN);
        passwordChangeRequest.setUserAccount(user);
        secondPasswordChangeRequest.setUserAccount(user);
        passwordChangeRequest.setCreatedAt(Instant.now());
        secondPasswordChangeRequest.setCreatedAt(Instant.now());
        transactional(() -> em.persist(passwordChangeRequest));
        transactional(() -> em.persist(secondPasswordChangeRequest));

        final Optional<PasswordChangeRequest> result = sut.findByToken(TOKEN);
        assertTrue(result.isPresent());
        assertEquals(TOKEN, result.get().getToken());
        assertEquals(user.getUri(), result.get().getUserAccount().getUri());
    }

    @Test
    void findByUsernameReturnsResultMatchingTheToken() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));

        final String TOKEN = randomToken();
        final PasswordChangeRequest passwordChangeRequest = new PasswordChangeRequest();
        passwordChangeRequest.setToken(TOKEN);
        passwordChangeRequest.setUserAccount(user);
        passwordChangeRequest.setCreatedAt(Instant.now());
        transactional(() -> em.persist(passwordChangeRequest));

        final Optional<PasswordChangeRequest> result = sut.findByUsername(user.getUsername());
        assertTrue(result.isPresent());
        assertEquals(TOKEN, result.get().getToken());
        assertEquals(user.getUri(), result.get().getUserAccount().getUri());
    }

    @Test
    void findByUsernameReturnsEmptyOptionalWhenNoMatchingRequestIsFound() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));

        final String TOKEN = randomToken();
        final PasswordChangeRequest passwordChangeRequest = new PasswordChangeRequest();
        passwordChangeRequest.setToken(TOKEN);
        passwordChangeRequest.setUserAccount(user);
        passwordChangeRequest.setCreatedAt(Instant.now());
        transactional(() -> em.persist(passwordChangeRequest));

        final Optional<PasswordChangeRequest> result = sut.findByUsername("invalidUsername@kbss.felk.cvut.cz");
        assertFalse(result.isPresent());
    }

    @Test
    void findByUsernameReturnsSingleResultWhenMultipleArePresent() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));

        final String TOKEN = randomToken();
        final String ANOTHER_TOKEN = randomToken();
        final PasswordChangeRequest passwordChangeRequest = new PasswordChangeRequest();
        final PasswordChangeRequest secondPasswordChangeRequest = new PasswordChangeRequest();
        passwordChangeRequest.setToken(TOKEN);
        secondPasswordChangeRequest.setToken(ANOTHER_TOKEN);
        passwordChangeRequest.setUserAccount(user);
        secondPasswordChangeRequest.setUserAccount(user);
        // make the password request the latest
        passwordChangeRequest.setCreatedAt(Instant.now().plusSeconds(1));
        secondPasswordChangeRequest.setCreatedAt(Instant.now());
        transactional(() -> em.persist(passwordChangeRequest));
        transactional(() -> em.persist(secondPasswordChangeRequest));

        final Optional<PasswordChangeRequest> result = sut.findByUsername(user.getUsername());
        assertTrue(result.isPresent());
        assertEquals(TOKEN, result.get().getToken());
        assertEquals(user.getUri(), result.get().getUserAccount().getUri());
    }

    @Test
    void findAllByUsernameReturnsAllResults() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));

        final String TOKEN = randomToken();
        final String ANOTHER_TOKEN = randomToken();
        final PasswordChangeRequest passwordChangeRequest = new PasswordChangeRequest();
        final PasswordChangeRequest secondPasswordChangeRequest = new PasswordChangeRequest();
        passwordChangeRequest.setToken(TOKEN);
        secondPasswordChangeRequest.setToken(ANOTHER_TOKEN);
        passwordChangeRequest.setUserAccount(user);
        secondPasswordChangeRequest.setUserAccount(user);
        passwordChangeRequest.setCreatedAt(Instant.now());
        secondPasswordChangeRequest.setCreatedAt(Instant.now());
        transactional(() -> em.persist(passwordChangeRequest));
        transactional(() -> em.persist(secondPasswordChangeRequest));

        final List<PasswordChangeRequest> result = sut.findAllByUsername(user.getUsername());
        assertTrue(result.stream().anyMatch(r -> passwordChangeRequest.getUri().equals(r.getUri())));
        assertTrue(result.stream().anyMatch(r -> secondPasswordChangeRequest.getUri().equals(r.getUri())));
        assertEquals(2, result.size());
    }

}
