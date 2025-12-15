package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.model.UserAccount;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class PersonalAccessTokenDao extends BaseDao<PersonalAccessToken> {
    protected PersonalAccessTokenDao(EntityManager em) {
        super(PersonalAccessToken.class, em);
    }

    public List<PersonalAccessToken> findAllByUserAccount(UserAccount userAccount) {
        Objects.requireNonNull(userAccount);
        try {
            return em.createQuery("SELECT DISTINCT token FROM " + type.getSimpleName() + " token WHERE token.owner = :userAccount", type)
                    .setParameter("userAccount", userAccount)
                     .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
