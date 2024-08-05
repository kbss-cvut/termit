package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.model.UserAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class PasswordChangeRequestDao extends BaseDao<PasswordChangeRequest> {

    @Autowired
    public PasswordChangeRequestDao(EntityManager em) {
        super(PasswordChangeRequest.class, em);
    }

    public List<PasswordChangeRequest> findAllByUserAccount(UserAccount userAccount) {
        Objects.requireNonNull(userAccount);
        try {
            return em.createQuery("SELECT DISTINCT t FROM " + type.getSimpleName() + " t WHERE t.userAccount = :userAccount", type)
                     .setParameter("userAccount", userAccount)
                     .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
