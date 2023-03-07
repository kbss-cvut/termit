package cz.cvut.kbss.termit.persistence.dao.acl;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AccessControlListDao {

    private final EntityManager em;

    private final DescriptorFactory descriptorFactory;

    public AccessControlListDao(EntityManager em, DescriptorFactory descriptorFactory) {
        this.em = em;
        this.descriptorFactory = descriptorFactory;
    }

    /**
     * Finds an {@link AccessControlList} guarding access of the specified subject.
     *
     * @param subject ACL subject
     * @return Matching ACL instance wrapped in an {@link Optional}, empty {@link Optional} if no such ACL exists
     */
    public Optional<AccessControlList> findFor(HasIdentifier subject) {
        Objects.requireNonNull(subject);
        try {
            return Optional.of(
                    em.createNativeQuery("SELECT ?acl WHERE { ?subject ?hasAcl ?acl . }", AccessControlList.class)
                      .setParameter("subject", subject)
                      .setParameter("hasAcl", URI.create(Vocabulary.s_p_ma_seznam_rizeni_pristupu))
                      .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Persists the specified {@link AccessControlList}.
     *
     * @param acl Access control list to persist
     */
    public void persist(AccessControlList acl) {
        Objects.requireNonNull(acl);
        try {
            em.persist(acl, descriptorFactory.accessControlListDescriptor());
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Updates the specified {@link AccessControlList}.
     *
     * @param acl Access control list to update
     */
    public void update(AccessControlList acl) {
        Objects.requireNonNull(acl);
        try {
            final AccessControlList original = em.find(AccessControlList.class, acl.getUri());
            assert original != null;
            removeOrphanRecords(original, acl);
            em.merge(acl, descriptorFactory.accessControlListDescriptor());
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private void removeOrphanRecords(AccessControlList original, AccessControlList update) {
        for (AccessControlRecord<?> r : Utils.emptyIfNull(original.getRecords())) {
            if (update.getRecords().stream().noneMatch(rr -> Objects.equals(r.getUri(), rr.getUri()))) {
                em.remove(r);
            }
        }
    }
}
