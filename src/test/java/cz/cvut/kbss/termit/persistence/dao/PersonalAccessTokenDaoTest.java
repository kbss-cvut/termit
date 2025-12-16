package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PersonalAccessTokenDaoTest extends BaseDaoTestRunner {
    @Autowired
    private EntityManager em;

    @Autowired
    private PersonalAccessTokenDao sut;

    private PersonalAccessToken createToken(UserAccount user) {
        PersonalAccessToken token = new PersonalAccessToken();
        token.setUri(Generator.generateUri());
        token.setOwner(user);
        transactional(() -> em.persist(token));
        return token;
    }

    private List<PersonalAccessToken> createTokens(UserAccount user) {
        final int toCreate = 5;
        List<PersonalAccessToken> tokens = new ArrayList<>(toCreate);
        for (int i = 0; i < toCreate; i++) {
            tokens.add(createToken(user));
        }
        return tokens;
    }

    @Test
    public void findAllByUserAccountReturnsAllTokensForUser() {
        final UserAccount userAccount = Generator.generateUserAccountWithPassword();
        final UserAccount otherUser = Generator.generateUserAccountWithPassword();
        transactional(() -> {
            em.persist(userAccount);
            em.persist(otherUser);
        });

        final List<PersonalAccessToken> expectedTokens = createTokens(userAccount);
        createTokens(otherUser);

        final List<PersonalAccessToken> tokens = sut.findAllByUserAccount(userAccount);
        assertEquals(expectedTokens.size(), tokens.size());
        assertTrue(expectedTokens.containsAll(tokens));

        for (PersonalAccessToken token : expectedTokens) {
            assertEquals(userAccount, token.getOwner());
        }
    }

    @Test
    public void findAllByUserAccountThrowsForNullAccount() {
        assertThrows(NullPointerException.class, () -> sut.findAllByUserAccount(null));
    }
}
