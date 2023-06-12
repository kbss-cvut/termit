package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.stereotype.Repository;

import java.util.Objects;

@Repository
public class UserGroupDao extends BaseDao<UserGroup> {

    private final DescriptorFactory descriptorFactory;

    public UserGroupDao(EntityManager em, DescriptorFactory descriptorFactory) {
        super(UserGroup.class, em);
        this.descriptorFactory = descriptorFactory;
    }

    @Override
    public void persist(UserGroup entity) {
        Objects.requireNonNull(entity);
        if (entity.getUri() == null) {
            entity.setUri(IdentifierResolver.generateSyntheticIdentifier(Vocabulary.s_c_Usergroup));
        }
        super.persist(entity);
    }

    @Override
    protected Descriptor getDescriptor() {
        return descriptorFactory.userGroupDescriptor();
    }
}
