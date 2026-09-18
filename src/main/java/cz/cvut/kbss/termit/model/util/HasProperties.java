package cz.cvut.kbss.termit.model.util;

import java.util.Map;
import java.util.Set;

/**
 * Implemented by entities with {@link cz.cvut.kbss.jopa.model.annotations.Properties Properties} attribute.
 */
public interface HasProperties {

    Map<String, Set<Object>> getProperties();

    void setProperties(Map<String, Set<Object>> properties);
}
