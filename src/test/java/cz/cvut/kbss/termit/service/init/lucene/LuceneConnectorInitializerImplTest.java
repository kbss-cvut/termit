package cz.cvut.kbss.termit.service.init.lucene;

import com.fasterxml.jackson.core.JsonProcessingException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.TransactionalTestRunner;
import cz.cvut.kbss.termit.environment.config.TestPersistenceConfig;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static cz.cvut.kbss.termit.service.init.lucene.LuceneConnectorInitializerImpl.LUCENE_CREATE_CONNECTOR;
import static cz.cvut.kbss.termit.service.init.lucene.LuceneConnectorInitializerImpl.LUCENE_DROP_CONNECTOR;
import static cz.cvut.kbss.termit.service.init.lucene.LuceneConnectorInitializerImpl.LUCENE_INSTANCE_NS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties(Configuration.class)
@ContextConfiguration(classes = {TestPersistenceConfig.class},
                      initializers = {ConfigDataApplicationContextInitializer.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class LuceneConnectorInitializerImplTest extends TransactionalTestRunner {

    @Autowired
    private EntityManager em;

    @Spy
    private Configuration configuration;

    private Configuration.Persistence config;

    private LuceneConnectorInitializerImpl sut;

    @BeforeEach
    void setUp() {
        this.sut = new LuceneConnectorInitializerImpl(configuration, em, Environment.getObjectMapper());
        this.config = configuration.getPersistence();
    }

    private List<URI> getConnectors() {
        // Note that in the testing environment, there is no lucene connector
        // we cant ask for existing connectors with its predicate
        // so the create predicate is inserted as real triple and we are selecting it here
        // we are also filtering out connectors that were removed
        return em.createNativeQuery("""
                SELECT ?cntUri {
                    ?cntUri ?createConnector [] .
                    FILTER NOT EXISTS {
                        ?cntUri ?dropConnector [] .
                    }
                }
                """, URI.class)
                 .setParameter("createConnector", LUCENE_CREATE_CONNECTOR)
                 .setParameter("dropConnector", LUCENE_DROP_CONNECTOR)
                 .getResultList();
    }

    private void assertConnectorsExist(Collection<String> languages) {
        Set<String> languagesSet = new HashSet<>(languages);
        languagesSet.add("");
        String labelIndexPrefix = LUCENE_INSTANCE_NS + config.getLuceneLabelIndexPrefix();
        String defcomIndexPrefix = LUCENE_INSTANCE_NS + config.getLuceneDefcomIndexPrefix();
        List<URI> connectors = getConnectors();
        // +1 for universal connector indexing all languages
        // *2 one label index, one defcom index
        assertEquals((languages.size() + 1) * 2, connectors.size());
        for(String lang : languagesSet) {
            assertTrue(connectors.contains(URI.create(labelIndexPrefix + lang)));
            assertTrue(connectors.contains(URI.create(defcomIndexPrefix + lang)));
        }
    }

    @Test
    void initializeCreatesDefaultConnectorsWithoutExplicitLanguage() {
        sut.initialize();
        assertConnectorsExist(Set.of());
    }

    @Test
    void initializeCreatesConnectorsForAllLanguages() {
        final List<String> languages = List.of("cs", "en", "pl", "as", "aa");
        final List<URI> predicates = Stream.of(Vocabulary.s_p_prefLabel,
                Vocabulary.s_p_altLabel, DC.Terms.TITLE, DC.Terms.DESCRIPTION, Vocabulary.s_p_definition)
                .map(URI::create).toList();
        assertEquals(languages.size(), predicates.size());
        transactional(() -> {
            for(int i = 0; i < languages.size(); i++) {
                em.createNativeQuery("INSERT DATA { ?uri ?pred ?value }")
                        .setParameter("uri", Generator.generateUri())
                        .setParameter("pred", predicates.get(i))
                        .setParameter("value", "stringValue", languages.get(i))
                        .executeUpdate();
            }
        });
        sut.initialize();
        assertConnectorsExist(languages);
    }

    private Query bindUriPredAndValue(Query q, URI uri, String language) {
        return q.setParameter("uri", uri)
                .setParameter("pred", URI.create(Vocabulary.s_p_altLabel))
                .setParameter("value", "stringValue", language);
    }
    private Query bindUriPredAndValue(Query q, URI uri) {
        return bindUriPredAndValue(q, uri, "pl");
    }

    @Test
    void initializeDropsConnectorsForNonExistingLanguages() {
        final URI uri = Generator.generateUri();
        transactional(() -> {
            bindUriPredAndValue(em.createNativeQuery("INSERT DATA { ?uri ?pred ?value }"), uri)
                    .executeUpdate();
        });
        sut.initialize();
        assertConnectorsExist(List.of("pl"));
        transactional(() -> {
            bindUriPredAndValue(em.createNativeQuery("DELETE WHERE { ?uri ?pred ?value }"), uri)
                    .executeUpdate();
        });
        sut.initialize();
    }

    @Test
    void initializeWontDropNonPrefixedConnectors() {
        final URI connectorUri = URI.create(LUCENE_INSTANCE_NS + "myCustomPrefix");
        transactional(() -> {
            em.createNativeQuery("INSERT DATA {?connectorUri ?createConnector [].}")
                    .setParameter("connectorUri", connectorUri)
                    .setParameter("createConnector", LUCENE_CREATE_CONNECTOR)
                    .executeUpdate();
        });
        sut.initialize();
        transactional(() -> {
            final int removedConnectorsCount =
                    em.createNativeQuery("SELECT ?connectorUri WHERE { ?connectorUri ?dropConnector ?value }")
                      .setParameter("dropConnector", LUCENE_DROP_CONNECTOR)
                      .getResultList().size();
            assertEquals(0, removedConnectorsCount);
        });
    }

    @ParameterizedTest
    @CsvSource({"cs,org.apache.lucene.analysis.cz.CzechAnalyzer",
                "cz,org.apache.lucene.analysis.cz.CzechAnalyzer"
    })
    void initializeSetsCorrectAnalyzerForCzech(String lang, String analyzer) {
        transactional(() -> {
            bindUriPredAndValue(em.createNativeQuery("INSERT DATA { ?uri ?pred ?value }"), Generator.generateUri(), lang)
                    .executeUpdate();
        });
        sut.initialize();
        transactional(() -> {
            List<String> analyzers = em.createNativeQuery("""
                    SELECT ?options WHERE {
                        ?uri ?createConnector ?options .
                        FILTER(REGEX(str(?uri), STR(?lang))) .
                    }
                """, String.class)
                                           .setParameter("createConnector", LUCENE_CREATE_CONNECTOR)
                                           .setParameter("lang", ".*" + lang + "$")
                                           .getResultStream()
                                           .map(options -> {
                                               try {
                                                   return Environment.getObjectMapper()
                                                                     .readTree(options)
                                                                     .get("analyzer")
                                                                     .asText();
                                               } catch (JsonProcessingException e) {
                                                   throw new RuntimeException(e);
                                               }
                                           }).toList();
            assertEquals(2, analyzers.size());
            analyzers.forEach(value -> assertEquals(analyzer, value));
        });
    }
}
