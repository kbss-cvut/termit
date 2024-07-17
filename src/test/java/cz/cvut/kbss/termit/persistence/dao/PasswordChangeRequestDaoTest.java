package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordChangeRequestDaoTest extends BaseDaoTestRunner {

    private static final String TOKEN_A = "a7a9a64f-ce3f-40e6-90ef-a2345c811715";

    private static final String TOKEN_B = "7dbcea7b-8567-44df-a64e-084d2bdfacc3";

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

        final String TOKEN = TOKEN_A;
        final String ANOTHER_TOKEN = TOKEN_B;

        final PasswordChangeRequest passwordChangeRequest = new PasswordChangeRequest();
        passwordChangeRequest.setToken(TOKEN);
        passwordChangeRequest.setUserAccount(user);
        passwordChangeRequest.setCreatedAt(Instant.now());
        transactional(() -> em.persist(passwordChangeRequest));

        final Optional<PasswordChangeRequest> result = sut.findByToken(ANOTHER_TOKEN);
        assertFalse(result.isPresent());
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

}
