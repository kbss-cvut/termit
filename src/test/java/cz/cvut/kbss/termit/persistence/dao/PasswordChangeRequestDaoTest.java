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

        final List<PasswordChangeRequest> result = sut.findAllByUserAccount(user);
        assertTrue(result.stream().anyMatch(r -> passwordChangeRequest.getUri().equals(r.getUri())));
        assertTrue(result.stream().anyMatch(r -> secondPasswordChangeRequest.getUri().equals(r.getUri())));
        assertEquals(2, result.size());
    }

}
