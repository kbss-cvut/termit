package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Objects;

@Repository
public class ChangeTrackingHelperDao {

    private final EntityManager em;

    private final DescriptorFactory descriptorFactory;

    @Autowired
    public ChangeTrackingHelperDao(EntityManager em, DescriptorFactory descriptorFactory) {
        this.em = em;
        this.descriptorFactory = descriptorFactory;
    }

    /**
     * Finds an existing stored instance of the specified asset.
     *
     * @param update Current state of the asset to find
     * @return Stored state of the searched asset
     */
    public <T extends Asset> T findStored(T update) {
        Objects.requireNonNull(update);
        final T result = (T) em.find(update.getClass(), update.getUri(), update.createDescriptor(descriptorFactory));
        if (result == null) {
            throw NotFoundException.create(update.getClass().getSimpleName(), update.getUri());
        }
        // We do not want the result to be in the persistence context when updates happen later (mainly to prevent issues with repository contexts)
        em.detach(result);
        return result;
    }
}
