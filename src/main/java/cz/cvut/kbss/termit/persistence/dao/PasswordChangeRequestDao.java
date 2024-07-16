package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
public class PasswordChangeRequestDao extends BaseDao<PasswordChangeRequest> {

    @Autowired
    public PasswordChangeRequestDao(EntityManager em) {
        super(PasswordChangeRequest.class, em);
    }

    public Optional<PasswordChangeRequest> find(String token) {
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
}
