package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
public class PasswordChangeRequestDao extends BaseDao<PasswordChangeRequest> {

    private final Configuration.Persistence persistenceConfig;

    @Autowired
    public PasswordChangeRequestDao(EntityManager em, Configuration configuration) {
        super(PasswordChangeRequest.class, em);
        this.persistenceConfig = configuration.getPersistence();
    }

    public Optional<PasswordChangeRequest> findByToken(String token) {
        Objects.requireNonNull(token);
        try {
            return Optional.of(
                    em.createQuery("SELECT t FROM " + type.getSimpleName() + " t WHERE t.token = :token", type)
                      .setParameter("token", token).getSingleResult()
            );
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    public Optional<PasswordChangeRequest> findByUsername(String username) {
        Objects.requireNonNull(username);
        try {
            return Optional.of(
                    em.createQuery("SELECT t FROM " + type.getSimpleName() + " t WHERE t.userAccount.username = :username", type)
                      .setParameter("username", username, persistenceConfig.getLanguage()).getSingleResult()
            );
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
