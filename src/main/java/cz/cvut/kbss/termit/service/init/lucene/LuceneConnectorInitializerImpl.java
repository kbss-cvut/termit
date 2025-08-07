package cz.cvut.kbss.termit.service.init.lucene;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/*
GraphDB documentation for Lucene connector
https://graphdb.ontotext.com/documentation/10.0/lucene-graphdb-connector.html#what-s-in-this-document

Links to lucene core javadoc listing available analyzers
https://lucene.apache.org/core/9_4_2/analysis/common/index.html

Following JavaScript can be used in browser console to get the map in analyzer-map.json.
Alternative would be adding lucene dependency and listing the analyzer classes at runtime.

(function() {
    const classLangMap = {};
    Promise.all(Array.from(
        document.querySelectorAll(".overviewSummary tbody th a").values().map(a =>
            fetch(a.href).then(result => result.text())
            .then(html => {
                const div = document.createElement("div")
                div.innerHTML = html;
                const className = div.querySelector("tbody th a").innerText;
                div.remove()
                if (className.endsWith("Analyzer")) {
                    classLangMap[a.innerText.split('.').pop()] = a.innerText + "." + className
                }
            })
    ))).then(() => console.log(JSON.stringify(classLangMap)))
})()
*/

/**
 * Finds all languages used by any object in database
 * and ensures that required lucene connectors are created in the database with up-to-date options for each language.
 * Remaining lucene connectors that are matching prefixes of {@link #requiredConnectors} are dropped.
 */
@Service
@Profile("lucene")
public class LuceneConnectorInitializerImpl implements LuceneConnectorInitializer {
    public static final String LUCENE_INSTANCE_NS = "http://www.ontotext.com/connectors/lucene/instance#";
    /**
     * Map from language codes to analyzer class names available in GraphDB
     */
    private final Map<String, String> analyzerMap;
    /**
     * Map from lucene connector prefix to it's options
     */
    private final Map<String, JsonNode> requiredConnectors;
    private final EntityManager em;
    private final ObjectMapper mapper;

    public LuceneConnectorInitializerImpl(Configuration configuration, EntityManager em, ObjectMapper mapper) {
        Configuration.Persistence config = configuration.getPersistence();
        this.em = em;
        this.mapper = mapper;

        this.requiredConnectors = Map.of(
                LUCENE_INSTANCE_NS + config.getLuceneLabelIndexPrefix(), loadConnectorJson("label.json", mapper),
                LUCENE_INSTANCE_NS + config.getLuceneDefcomIndexPrefix(), loadConnectorJson("defcom.json", mapper)
        );
        this.analyzerMap = loadAnalyzersMap(mapper);
    }

    /**
     * Loads {@code analyzer-map.json} file and ensures that the map is not empty.
     * @param mapper the {@link ObjectMapper} used for deserialization.
     * @return deserialized map.
     */
    private static Map<String, String> loadAnalyzersMap(ObjectMapper mapper) {
        final Map<String, String> map = mapper.convertValue(loadConnectorJson("analyzer-map.json", mapper), Map.class);
        if (map.isEmpty()) {
            throw new TermItException("Lucene analyzer map cannot be empty!");
        }
        return map;
    }

    /**
     * Loads file with {@code name} from directory {@code lucene} on the classpath.
     * @param name file name in {@code lucene} resource directory
     * @return Loaded JSON tree
     */
    private static JsonNode loadConnectorJson(String name, ObjectMapper mapper) {
        ClassLoader cl = LuceneConnectorInitializerImpl.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream("lucene/" + name)) {
            return mapper.readTree(in);
        } catch (Exception e) {
            throw new TermItException("Failed to load connector JSON " + name, e);
        }
    }

    /**
     * Retrieves all languages used by objects in the database.
     * @return the set of language codes, it is allowed to contain empty string
     */
    private Set<String> fetchUsedLanguages() {
        return Set.copyOf(em.createNativeQuery("""
                SELECT DISTINCT ?lang {
                    ?subject ?pred ?object .
                    BIND(lang(?object) AS ?lang) .
                    FILTER(BOUND(?lang)) .
                }
                """, String.class).getResultList());
    }

    /**
     * Fetch options for the specified lucene connector.
     * @param uri The URI of the lucene connector.
     * @return the lucene connector
     */
    private LuceneConnector fetchLuceneConnector(URI uri) {
        final String createString = em.createNativeQuery("""
                                              SELECT ?createString {
                                                  ?uri <http://www.ontotext.com/connectors/lucene#listOptionValues> ?createString .
                                              }
                                              """, String.class)
                                      .setParameter("uri", uri)
                                      .getSingleResult();
        try {
            return new LuceneConnector(uri, mapper.readTree(createString));
        } catch (JsonProcessingException e) {
            throw new PersistenceException("Error while reading Lucene connector options!", e);
        }
    }

    /**
     * Finds all lucene connectors in the database
     * @return the list of lucene connectors
     */
    private List<LuceneConnector> fetchLuceneConnectors() {
        return em.createNativeQuery("""
                SELECT ?cntUri {
                    ?cntUri <http://www.ontotext.com/connectors/lucene#listConnectors> [] .
                }
                """, URI.class).getResultStream().map(this::fetchLuceneConnector).toList();
    }

    /**
     * Loads existing connectors that have matching URI prefix to {@link #requiredConnectors}.
     * @return Map from language tag to loaded connectors
     */
    private Map<String, Set<LuceneConnector>> loadExistingConnectors() {
        final List<LuceneConnector> allConnectors = fetchLuceneConnectors();
        final Map<String, Set<LuceneConnector>> languageConnectors = new HashMap<>(allConnectors.size() / 2);
        for (LuceneConnector connector : allConnectors) {
            String sUri = connector.uri().toString();
            String language = null;

            for (String prefix : requiredConnectors.keySet()) {
                if (sUri.startsWith(prefix)) {
                    language = sUri.substring(prefix.length());
                    break;
                }
            }

            if (language != null) {
                languageConnectors.computeIfAbsent(language, k -> new HashSet<>(2)).add(connector);
            }
        }
        return languageConnectors;
    }

    /**
     * Finds connector in {@code connectors} set matching the {@code uri}
     * @param uri the URI prefix to match
     * @param connectors the set to search
     * @return connector with matching {@code uri} or {@code null}
     */
    private LuceneConnector findConnector(URI uri, Set<LuceneConnector> connectors) {
        for (LuceneConnector connector : connectors) {
            if (uri.equals(connector.uri())) {
                return connector;
            }
        }
        return null;
    }

    /**
     * Drops the lucene connector from database.
     * @param connectorUri The connector to drop.
     */
    private void dropConnector(URI connectorUri) {
        Objects.requireNonNull(connectorUri);
        em.createNativeQuery("""
                  INSERT DATA {
                      ?connectorUri <http://www.ontotext.com/connectors/lucene#dropConnector> [].
                  }
                  """)
          .setParameter("connectorUri", connectorUri)
          .executeUpdate();
    }

    /**
     * Takes specified options and adds {@code languages} and {@code analyzer} property to the top-level object.
     * @param language the language to add and use for analyzer selection
     * @param options the options JSON
     * @return serialized {@code options} with added properties
     */
    private String createOptionsForConnector(String language, JsonNode options) {
        JsonNode newOptions = options.deepCopy();
        if (newOptions.isObject() && newOptions instanceof ObjectNode objectNode) {
            objectNode.withArrayProperty("languages").add(language);
            String analyzer = analyzerMap.get(language);
            if (analyzer != null) {
                objectNode.put("analyzer", analyzer);
            }
        } else {
            throw new TermItException("Connector options must be an object");
        }

        return newOptions.toString();
    }

    /**
     * Creates new lucene connector with {@code prefix + language} URI
     * and with specified options customized for the language.
     * @param connectorUri The uri of the new connector
     * @param language The language to index
     * @param options The options to initialize connector with
     */
    private void createConnector(URI connectorUri, String language, JsonNode options) {
        Objects.requireNonNull(connectorUri);
        em.createNativeQuery("""
                INSERT DATA {
                    ?connectorUri <http://www.ontotext.com/connectors/lucene#createConnector> ?options ..
                }
                """)
          .setParameter("connectorUri", connectorUri)
          .setParameter("options", createOptionsForConnector(language, options))
          .executeUpdate();
    }

    /**
     * Ensures that each required connector for the {@code language} exists in the {@code existingConnectors}
     * @param language the indexed language
     * @param existingConnectors set to search in
     */
    private void handleRequiredConnectors(String language, Set<LuceneConnector> existingConnectors) {
        Set<LuceneConnector> remaining = new HashSet<>(existingConnectors);
        for (Map.Entry<String, JsonNode> required : requiredConnectors.entrySet()) {
            String requiredPrefix = required.getKey();
            JsonNode requiredOptions = required.getValue();

            URI connectorUri = URI.create(requiredPrefix + language);
            LuceneConnector connector = findConnector(connectorUri, remaining);
            remaining.remove(connector);
            if (connector != null && !requiredOptions.equals(connector.options())) {
                // Drop existing connector if it is not matching required options
                dropConnector(connector.uri());
                connector = null;
            }

            // create connector if the connector does not exist or was dropped
            if (connector == null) {
                createConnector(connectorUri, language, requiredOptions);
            }
        }
        // Drop all remaining connectors
        for (LuceneConnector connector : remaining) {
            dropConnector(connector.uri());
        }
    }

    /**
     * Finds all languages used by any object in database
     * and ensures that required lucene connectors are created in the database with up-to-date options for each language.
     * Remaining lucene connectors that are matching prefixes of {@link #requiredConnectors} are dropped.
     */
    @Override
    public void initialize() {
        final Map<String, Set<LuceneConnector>> connectors = loadExistingConnectors();
        final Set<String> languages = fetchUsedLanguages();
        for (String lang : languages) {
            handleRequiredConnectors(lang, connectors.getOrDefault(lang, Set.of()));
        }
    }


}
