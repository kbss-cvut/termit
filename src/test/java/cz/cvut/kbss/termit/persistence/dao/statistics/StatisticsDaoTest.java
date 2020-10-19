package cz.cvut.kbss.termit.persistence.dao.statistics;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class StatisticsDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private StatisticsDao sut;
}
