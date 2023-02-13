package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.model.UserGroup;
import org.springframework.stereotype.Repository;

@Repository
public class UserGroupDao extends BaseDao<UserGroup> {

    public UserGroupDao(EntityManager em) {
        super(UserGroup.class, em);
    }
}
