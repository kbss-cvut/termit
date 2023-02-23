package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.stereotype.Repository;

import java.net.URI;

@Repository
public class UserGroupDao extends BaseDao<UserGroup> {

    static final URI CONTEXT = URI.create(Vocabulary.s_c_Usergroup);

    public UserGroupDao(EntityManager em) {
        super(UserGroup.class, em);
    }

    @Override
    protected Descriptor getDescriptor() {
        final EntityDescriptor descriptor = new EntityDescriptor(CONTEXT);
        descriptor.addAttributeContext(em.getMetamodel().entity(UserGroup.class).getAttribute("members"), null);
        return descriptor;
    }
}
