package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Repository
public class ChangeRecordDao {

    private final ChangeTrackingContextResolver contextResolver;

    private final EntityManager em;

    public ChangeRecordDao(ChangeTrackingContextResolver contextResolver, EntityManager em) {
        this.contextResolver = contextResolver;
        this.em = em;
    }

    /**
     * Persists the specified change record into the specified repository context.
     *
     * @param record       Record to save
     * @param changedAsset The changed asset
     */
    public void persist(AbstractChangeRecord record, Asset<?> changedAsset) {
        Objects.requireNonNull(record);
        final EntityDescriptor descriptor = new EntityDescriptor(
                contextResolver.resolveChangeTrackingContext(changedAsset));
        descriptor.addAttributeDescriptor(em.getMetamodel().entity(AbstractChangeRecord.class).getAttribute("author"),
                new EntityDescriptor());
        descriptor.setLanguage(null);
        try {
            em.persist(record, descriptor);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Finds all change records to the specified asset.
     *
     * @param asset The changed asset
     * @return List of change records ordered by timestamp (descending)
     */
    public List<AbstractChangeRecord> findAll(Asset<?> asset) {
        Objects.requireNonNull(asset);
        try {
            final Descriptor descriptor = new EntityDescriptor();
            descriptor.setLanguage(null);
            return em.createNativeQuery("SELECT ?r WHERE {" +
                    "?r a ?changeRecord ;" +
                    "?relatesTo ?asset ;" +
                    "?hasTime ?timestamp ." +
                    "OPTIONAL { ?r ?hasChangedAttribute ?attribute . }" +
                    "} ORDER BY DESC(?timestamp) ?attribute", AbstractChangeRecord.class)
                     .setParameter("changeRecord", URI.create(Vocabulary.s_c_zmena))
                     .setParameter("relatesTo", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                     .setParameter("hasChangedAttribute", URI.create(Vocabulary.s_p_ma_zmeneny_atribut))
                     .setParameter("hasTime", URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                     .setParameter("asset", asset.getUri()).setDescriptor(descriptor).getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
