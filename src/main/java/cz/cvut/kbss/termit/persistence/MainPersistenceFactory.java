/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
import cz.cvut.kbss.jopa.model.JOPAPersistenceProperties;
import cz.cvut.kbss.jopa.model.JOPAPersistenceProvider;
import cz.cvut.kbss.ontodriver.config.OntoDriverProperties;
import cz.cvut.kbss.ontodriver.rdf4j.config.Rdf4jOntoDriverProperties;
import cz.cvut.kbss.termit.event.EvictCacheEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

import java.util.HashMap;
import java.util.Map;

import static cz.cvut.kbss.jopa.model.JOPAPersistenceProperties.DATA_SOURCE_CLASS;
import static cz.cvut.kbss.jopa.model.JOPAPersistenceProperties.JPA_PERSISTENCE_PROVIDER;
import static cz.cvut.kbss.jopa.model.JOPAPersistenceProperties.LANG;
import static cz.cvut.kbss.jopa.model.JOPAPersistenceProperties.ONTOLOGY_PHYSICAL_URI_KEY;
import static cz.cvut.kbss.jopa.model.JOPAPersistenceProperties.PREFER_MULTILINGUAL_STRING;
import static cz.cvut.kbss.jopa.model.JOPAPersistenceProperties.SCAN_PACKAGE;

/**
 * Sets up persistence and provides {@link EntityManagerFactory} as Spring bean.
 */
@Configuration
@Profile("!test")
public class MainPersistenceFactory {

    private final cz.cvut.kbss.termit.util.Configuration configuration;

    private EntityManagerFactory emf;

    @Autowired
    public MainPersistenceFactory(cz.cvut.kbss.termit.util.Configuration configuration) {
        this.configuration = configuration;
    }

    @Bean
    @Primary
    public EntityManagerFactory getEntityManagerFactory() {
        return emf;
    }

    @PostConstruct
    private void init() {
        final Map<String, String> properties = defaultParams();
        properties.put(ONTOLOGY_PHYSICAL_URI_KEY, configuration.getRepository().getUrl());
        properties.put(DATA_SOURCE_CLASS, configuration.getPersistence().getDriver());
        properties.put(LANG, configuration.getPersistence().getLanguage());
        properties.put(PREFER_MULTILINGUAL_STRING, Boolean.TRUE.toString());
        if (configuration.getRepository().getUsername() != null) {
            properties.put(OntoDriverProperties.DATA_SOURCE_USERNAME, configuration.getRepository().getUsername());
            properties.put(OntoDriverProperties.DATA_SOURCE_PASSWORD, configuration.getRepository().getPassword());
        }
        // OPTIMIZATION: Always use statement retrieval with unbound property. Should spare repository queries
        properties.put(Rdf4jOntoDriverProperties.LOAD_ALL_THRESHOLD, "1");
        properties.put(JOPAPersistenceProperties.LRU_CACHE_CAPACITY, "32768");
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

    @EventListener
    public void onEvictCache(EvictCacheEvent evt) {
        emf.getCache().evictAll();
    }
}
