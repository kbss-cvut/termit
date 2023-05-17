package cz.cvut.kbss.termit.persistence.dao.acl;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
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
        return Optional.ofNullable(em.find(AccessControlList.class, id, descriptor()));
    }

    private Descriptor descriptor() {
        return descriptorFactory.accessControlListDescriptor();
    }

    /**
     * Gets a reference to an {@link AccessControlList} with the specified identifier.
     *
     * @param id ACL identifier
     * @return Matching ACL reference wrapped in an {@link Optional}, empty {@link Optional} if no such ACL exists
     */
    public Optional<AccessControlList> getReference(URI id) {
        return Optional.ofNullable(
                em.getReference(AccessControlList.class, id, descriptor()));
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
                      .setParameter("subject", subject.getUri())
                      .setParameter("hasAcl", URI.create(Vocabulary.s_p_ma_seznam_rizeni_pristupu))
                      .setDescriptor(descriptor())
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
        em.persist(acl, descriptor());
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
        em.merge(acl, descriptor());
    }

    private void removeOrphanRecords(AccessControlList original, AccessControlList update) {
        for (AccessControlRecord<?> r : Utils.emptyIfNull(original.getRecords())) {
            if (update.getRecords().stream().noneMatch(rr -> Objects.equals(r.getUri(), rr.getUri()))) {
                em.remove(r);
            }
        }
    }

    /**
     * Removes the specified access control list and all its records.
     *
     * @param acl Access control list to remove
     */
    public void remove(AccessControlList acl) {
        Objects.requireNonNull(acl);
        final AccessControlList toRemove = em.find(AccessControlList.class, acl.getUri(), descriptor());
        if (toRemove != null) {
            em.remove(toRemove);
        }
    }

    /**
     * Gets identifier of the subject of the specified {@link AccessControlList}.
     *
     * @param acl ACL whose subject to resolve
     * @return Identifier of the matching asset, empty {@code Optional} if no matching one is found
     */
    public Optional<URI> resolveSubjectOf(AccessControlList acl) {
        Objects.requireNonNull(acl);
        try {
            return Optional.of(em.createNativeQuery("SELECT ?x WHERE { ?x ?hasAcl ?acl . }", URI.class)
                                 .setParameter("hasAcl", URI.create(Vocabulary.s_p_ma_seznam_rizeni_pristupu))
                                 .setParameter("acl", acl).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
