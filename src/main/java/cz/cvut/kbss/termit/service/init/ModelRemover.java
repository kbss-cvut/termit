package cz.cvut.kbss.termit.service.init;

import cz.cvut.kbss.jopa.model.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

/**
 * Removes all references to the class Model and its instances.
 * <p>
 * This class should be removed as soon as we determine that no existing instances contain the obsolete Model data.
 */
@Service
public class ModelRemover {

    private final EntityManager em;

    public ModelRemover(EntityManager em) {
        this.em = em;
    }

    /**
     * Removes all instances of class model, their snapshots, and all related data.
     */
    @Transactional
    public void removeModel() {
        final URI modelType = URI.create("http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/model");
        em.createNativeQuery("DELETE WHERE {" +
                                     "?x ?y ?z ." +
                                     "?z a ?modelType ." +
                                     "}").setParameter("modelType", modelType).executeUpdate();
        em.createNativeQuery("DELETE WHERE {" +
                                     "?x ?y ?z ." +
                                     "?x a ?modelType ." +
                                     "}").setParameter("modelType", modelType).executeUpdate();
    }
}
