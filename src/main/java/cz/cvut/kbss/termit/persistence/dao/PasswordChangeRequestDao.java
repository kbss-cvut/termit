package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class PasswordChangeRequestDao extends BaseDao<PasswordChangeRequest> {

    private final Configuration.Persistence persistenceConfig;

    @Autowired
    public PasswordChangeRequestDao(EntityManager em, Configuration configuration) {
        super(PasswordChangeRequest.class, em);
        this.persistenceConfig = configuration.getPersistence();
    }

    public List<PasswordChangeRequest> findAllByUsername(String username) {
        Objects.requireNonNull(username);
        try {
            return em.createQuery("SELECT DISTINCT t FROM " + type.getSimpleName() + " t WHERE t.userAccount.username = :username", type)
                     .setParameter("username", username, persistenceConfig.getLanguage())
                     .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
