package cz.cvut.kbss.termit.service.init.lucene;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.exception.ResourceNotFoundException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/*
GraphDB documentation for Lucene connector
https://graphdb.ontotext.com/documentation/10.0/lucene-graphdb-connector.html#what-s-in-this-document

Links to lucene core javadoc listing available analyzers
The link is the same from GraphDB 10 and 11 documentation
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
    ))).then(() => {
        // all the tags are matching IETF BCP 47 language sub-tags except for Czech :)
        classLangMap["cs"] = classLangMap["cz"]
        const ordered = Object.keys(classLangMap).sort().reduce(
          (obj, key) => {
            obj[key] = classLangMap[key];
            return obj;
          },
          {}
        );
        console.log(JSON.stringify(ordered))
    })
})()
*/

/**
 * Finds all languages used by any object in database identified by an indexed predicate
 * and ensures that required lucene connectors are created in the database with up-to-date options for each language.
 * Remaining lucene connectors that are matching prefixes of {@link #requiredConnectors} are dropped.
 */
@Service
@Profile("!test")
public class GraphDBLuceneConnectorInitializer {
    static final URI LUCENE_LIST_CONNECTORS = URI.create("http://www.ontotext.com/connectors/lucene#listConnectors");
    static final URI LUCENE_LIST_OPTION_VALUES = URI.create("http://www.ontotext.com/connectors/lucene#listOptionValues");
    static final URI LUCENE_DROP_CONNECTOR = URI.create("http://www.ontotext.com/connectors/lucene#dropConnector");
    static final URI LUCENE_CREATE_CONNECTOR = URI.create("http://www.ontotext.com/connectors/lucene#createConnector");
    private static final Logger LOG = LoggerFactory.getLogger(GraphDBLuceneConnectorInitializer.class);
    /**
     * Map from language codes to analyzer class names available in GraphDB.
     * <p>
     * Loaded from {@code resources/lucene/analyzer-map.json}
     */
    private final Map<String, String> analyzerMap;
    /**
     * Map from lucene connector prefix to it's options
     */
    private final Map<String, JsonNode> requiredConnectors;
    /**
     * Every last predicate from each connector {@code fields[].propertyChain}.
     * The predicate is excluded if it is not a valid URI.
     */
    private final Set<URI> indexedFields;
    private final EntityManager em;
    private final ObjectMapper mapper;

    public GraphDBLuceneConnectorInitializer(EntityManager em, ObjectMapper mapper) {
        this.em = em;
        this.mapper = mapper;

        LOG.trace("Loading lucene connectors options from JSON files");

        this.requiredConnectors = Map.of(
                Constants.LUCENE_CONNECTOR_LABEL_INDEX_PREFIX, loadConnectorJson("label.json", mapper),
                Constants.LUCENE_CONNECTOR_DEFCOM_INDEX_PREFIX, loadConnectorJson("defcom.json", mapper)
        );
        this.indexedFields = resolveIndexedLiterals(requiredConnectors.values());
        this.analyzerMap = loadAnalyzersMap(mapper);
    }

    /**
     * Inspects provided connectors options and extracts a set containing the last predicate from each {@code fields[].propertyChain}
     * as long as it is a valid URI.
     * @param connectorsOptions connectors options to inspect
     * @return Set of last propertyChain predicates
     */
    private static Set<URI> resolveIndexedLiterals(Collection<JsonNode> connectorsOptions) {
        Set<String> indexedLiterals = new HashSet<>();
        for (JsonNode connectorOptions : connectorsOptions) { // for each connector
            if (connectorOptions.get("fields") instanceof ArrayNode fields) { // get fields property (which is an array)
                for (Iterator<JsonNode> it = fields.values(); it.hasNext(); ) { // for each field
                    JsonNode field = it.next();
                    JsonNode chainArray = field.get("propertyChain");
                    if (chainArray != null && chainArray.isArray()) { // extract the last node of propertyChain property
                        indexedLiterals.add(chainArray.get(chainArray.size() - 1).textValue());
                    } else {
                        throw new TermItException("Connector field is missing propertyChain property or it is not an array!");
                    }
                }
            } else {
                throw new TermItException("Connector is missing fields property or it is not an array!");
            }
        }
        return indexedLiterals.stream().map(str -> {
            try {
                return URI.create(str);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());
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
        ClassLoader cl = GraphDBLuceneConnectorInitializer.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream("lucene/" + name)) {
            return mapper.readTree(in);
        } catch (Exception e) {
            throw new ResourceNotFoundException("Failed to load connector JSON lucene/" + name);
        }
    }

    /**
     * Retrieves all languages used by {@link #indexedFields}
     * @return the set of language codes, it is allowed to contain empty string
     */
    private Set<String> fetchUsedLanguages() {
        return new HashSet<>(em.createNativeQuery("""
                SELECT DISTINCT ?lang {
                    ?subject ?pred ?object .
                    FILTER(?pred in (?indexedFields)) .
                    BIND(lang(?object) AS ?lang) .
                    FILTER(BOUND(?lang)) .
                }
                """, String.class)
                               .setParameter("indexedFields", indexedFields)
                               .getResultList());
    }

    /**
     * Fetch options for the specified lucene connector.
     * @param uri The URI of the lucene connector.
     * @return the lucene connector
     */
    private LuceneConnector fetchLuceneConnector(URI uri) {
        final String createString = em.createNativeQuery("""
                                              SELECT ?createString {
                                                  ?uri ?listOptionValues ?createString .
                                              }
                                              """, String.class)
                                      .setParameter("uri", uri)
                                      .setParameter("listOptionValues", LUCENE_LIST_OPTION_VALUES)
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
                    ?cntUri ?listConnectors [] .
                }
                """, URI.class)
                 .setParameter("listConnectors", LUCENE_LIST_CONNECTORS)
                 .getResultStream().map(this::fetchLuceneConnector).toList();
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
        LOG.trace("Dropping Lucene connector {}", connectorUri);
        em.createNativeQuery("""
                  INSERT DATA {
                      ?connectorUri ?dropConnector [].
                  }
                  """)
          .setParameter("connectorUri", connectorUri)
          .setParameter("dropConnector", LUCENE_DROP_CONNECTOR)
          .executeUpdate();
    }

    /**
     * Takes specified options and adds {@code languages} and {@code analyzer} property to the top-level object.
     * @param language the language to add and use for analyzer selection
     * @param options the options JSON
     * @return Copy of {@code options} with added properties
     */
    private JsonNode createOptionsForConnector(String language, JsonNode options) {
        JsonNode newOptions = options.deepCopy();
        if (Utils.isBlank(language)) {
            return newOptions;
        }
        if (newOptions.isObject() && newOptions instanceof ObjectNode objectNode) {
            objectNode.withArrayProperty("languages").add(language);
            String analyzer = analyzerMap.get(language);
            if (analyzer != null) {
                objectNode.put("analyzer", analyzer);
            }
        } else {
            throw new TermItException("Connector options must be an object");
        }

        return newOptions;
    }

    /**
     * Creates new lucene connector with specified options.
     * @param connectorUri The uri of the new connector
     * @param options The options to initialize connector with
     */
    private void createConnector(URI connectorUri, JsonNode options) {
        Objects.requireNonNull(connectorUri);
        LOG.trace("Creating Lucene connector {}", connectorUri);
        em.createNativeQuery("""
                INSERT DATA {
                    ?connectorUri ?createConnector ?options .
                }
                """)
          .setParameter("connectorUri", connectorUri)
          .setParameter("options", options.toString())
          .setParameter("createConnector", LUCENE_CREATE_CONNECTOR)
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
            JsonNode requiredOptions = createOptionsForConnector(language, required.getValue());

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
                createConnector(connectorUri, requiredOptions);
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
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void initialize() {
        LOG.debug("Initializing Lucene Connectors");
        final Map<String, Set<LuceneConnector>> connectors = loadExistingConnectors();
        final Set<String> languages = fetchUsedLanguages();
        languages.add(""); // explicitly add empty language to force creation of universal index for all languages
        for (String lang : languages) {
            handleRequiredConnectors(lang, connectors.getOrDefault(lang, Set.of()));
        }
        LOG.debug("Lucene Connectors initialized");
    }

}
