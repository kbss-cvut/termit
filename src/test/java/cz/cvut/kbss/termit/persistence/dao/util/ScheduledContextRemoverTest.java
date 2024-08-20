package cz.cvut.kbss.termit.persistence.dao.util;

import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;

class ScheduledContextRemoverTest extends BaseDaoTestRunner {
//
//    @Autowired
//    private EntityManager em;
//
//    @Autowired
//    private ScheduledContextRemover sut;
//
//    @Test
//    void runContextRemovalDropsContextsRegisteredForRemoval() {
//        final Set<URI> graphs = generateGraphs();
//        graphs.forEach(sut::scheduleForRemoval);
//
//        sut.runContextRemoval();
//        graphs.forEach(g -> assertFalse(
//                em.createNativeQuery("ASK { ?g ?y ?z . }", Boolean.class).setParameter("g", g).getSingleResult()));
//    }
//
//    private Set<URI> generateGraphs() {
//        final Set<URI> result = new HashSet<>();
//        transactional(() -> {
//            for (int i = 0; i < 5; i++) {
//                final URI graphUri = Generator.generateUri();
//                em.createNativeQuery("INSERT DATA { GRAPH ?g { ?g a ?type } }", Void.class)
//                  .setParameter("g", graphUri)
//                  .setParameter("type", URI.create(RDFS.RESOURCE))
//                  .executeUpdate();
//                result.add(graphUri);
//            }
//        });
//        return result;
//    }
}
