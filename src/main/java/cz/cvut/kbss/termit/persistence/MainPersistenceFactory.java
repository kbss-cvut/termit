/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.persistence;

import cz.cvut.kbss.jopa.Persistence;
import cz.cvut.kbss.jopa.model.EntityManagerFactory;
import cz.cvut.kbss.jopa.model.JOPAPersistenceProvider;
import cz.cvut.kbss.ontodriver.config.OntoDriverProperties;
import cz.cvut.kbss.ontodriver.sesame.config.SesameOntoDriverProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;

import static cz.cvut.kbss.jopa.model.JOPAPersistenceProperties.*;
import static cz.cvut.kbss.termit.util.ConfigParam.*;

/**
 * Sets up persistence and provides {@link EntityManagerFactory} as Spring bean.
 */
@Configuration
public class MainPersistenceFactory {

    private final cz.cvut.kbss.termit.util.Configuration config;

    private EntityManagerFactory emf;

    @Autowired
    public MainPersistenceFactory(cz.cvut.kbss.termit.util.Configuration config) {
        this.config = config;
    }

    @Bean
    @Primary
    public EntityManagerFactory getEntityManagerFactory() {
        return emf;
    }

    @PostConstruct
    private void init() {
        // Allow Apache HTTP client used by RDF4J to use a larger connection pool
        // Temporary, should be configurable via JOPA
        System.setProperty("http.maxConnections", "20");
        final Map<String, String> properties = defaultParams();
        properties.put(ONTOLOGY_PHYSICAL_URI_KEY, config.get(REPOSITORY_URL));
        properties.put(DATA_SOURCE_CLASS, config.get(DRIVER));
        properties.put(LANG, config.get(LANGUAGE));
        properties.put(PREFER_MULTILINGUAL_STRING, Boolean.TRUE.toString());
        if (config.contains(REPO_USERNAME)) {
            properties.put(OntoDriverProperties.DATA_SOURCE_USERNAME, config.get(REPO_USERNAME));
            properties.put(OntoDriverProperties.DATA_SOURCE_PASSWORD, config.get(REPO_PASSWORD));
        }
        // OPTIMIZATION: Always use statement retrieval with unbound property. Should spare repository queries
        properties.put(SesameOntoDriverProperties.SESAME_LOAD_ALL_THRESHOLD, "1");
        this.emf = Persistence.createEntityManagerFactory("termitPU", properties);
    }

    @PreDestroy
    private void close() {
        if (emf.isOpen()) {
            emf.close();
        }
    }

    /**
     * Default persistence unit configuration parameters.
     * <p>
     * These include: package scan for entities, provider specification
     *
     * @return Map with defaults
     */
    public static Map<String, String> defaultParams() {
        final Map<String, String> map = new HashMap<>();
        map.put(SCAN_PACKAGE, "cz.cvut.kbss.termit");
        map.put(JPA_PERSISTENCE_PROVIDER, JOPAPersistenceProvider.class.getName());
        return map;
    }
}
