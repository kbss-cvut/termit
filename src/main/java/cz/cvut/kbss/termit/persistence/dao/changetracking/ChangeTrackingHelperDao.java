package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.model.util.AssetVisitor;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
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
    public <T extends Asset<?>> T findStored(T update) {
        Objects.requireNonNull(update);
        final DescriptorBuilder descriptorBuilder = new DescriptorBuilder(descriptorFactory);
        update.accept(descriptorBuilder);
        final T result = (T) em.find(update.getClass(), update.getUri(), descriptorBuilder.descriptor);
        if (result == null) {
            throw NotFoundException.create(update.getClass().getSimpleName(), update.getUri());
        }
        // We do not want the result to be in the persistence context when updates happen later (mainly to prevent issues with repository contexts)
        em.detach(result);
        return result;
    }

    private static class DescriptorBuilder implements AssetVisitor {

        private final DescriptorFactory descriptorFactory;
        private Descriptor descriptor;

        private DescriptorBuilder(DescriptorFactory descriptorFactory) {
            this.descriptorFactory = descriptorFactory;
        }

        @Override
        public void visitTerm(AbstractTerm term) {
            this.descriptor = descriptorFactory.termDescriptor(term);
        }

        @Override
        public void visitVocabulary(Vocabulary vocabulary) {
            this.descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        }

        @Override
        public void visitResources(Resource resource) {
            // Do nothing special
            this.descriptor = new EntityDescriptor();
        }
    }
}
