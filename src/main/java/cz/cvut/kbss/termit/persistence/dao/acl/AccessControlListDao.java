package cz.cvut.kbss.termit.persistence.dao.acl;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
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
     * Finds an {@link AccessControlList} with the specified identifier.
     *
     * @param id ACL identifier
     * @return Matching ACL instance wrapped in an {@link Optional}, empty {@link Optional} if no such ACL exists
     */
    public Optional<AccessControlList> find(URI id) {
        return Optional.ofNullable(
                em.find(AccessControlList.class, id, descriptorFactory.accessControlListDescriptor()));
    }

    /**
     * Gets a reference to an {@link AccessControlList} with the specified identifier.
     *
     * @param id ACL identifier
     * @return Matching ACL reference wrapped in an {@link Optional}, empty {@link Optional} if no such ACL exists
     */
    public Optional<AccessControlList> getReference(URI id) {
        return Optional.ofNullable(
                em.getReference(AccessControlList.class, id, descriptorFactory.accessControlListDescriptor()));
    }

    /**
     * Finds an {@link AccessControlList} guarding access to the specified subject.
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
                      .setDescriptor(descriptorFactory.accessControlListDescriptor())
                      .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Persists the specified {@link AccessControlList}.
     *
     * @param acl Access control list to persist
     */
    public void persist(AccessControlList acl) {
        Objects.requireNonNull(acl);
        em.persist(acl, descriptorFactory.accessControlListDescriptor());
    }

    /**
     * Updates the specified {@link AccessControlList}.
     * <p>
     * Removes any orphaned {@link AccessControlRecord}s.
     *
     * @param acl Access control list to update
     */
    public void update(AccessControlList acl) {
        Objects.requireNonNull(acl);
        final AccessControlList original = em.find(AccessControlList.class, acl.getUri());
        assert original != null;
        removeOrphanRecords(original, acl);
        em.merge(acl, descriptorFactory.accessControlListDescriptor());
    }

    private void removeOrphanRecords(AccessControlList original, AccessControlList update) {
        for (AccessControlRecord<?> r : Utils.emptyIfNull(original.getRecords())) {
            if (update.getRecords().stream().noneMatch(rr -> Objects.equals(r.getUri(), rr.getUri()))) {
                em.remove(r);
            }
        }
    }
}
