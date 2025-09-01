package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.event.VocabularyCreatedEvent;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import jakarta.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic concerning external vocabularies imported from SPARQL repository.
 */
@Service
public class SparqlExternalVocabularyService implements ExternalVocabularyService{
    
    private static final Logger LOG = LoggerFactory.getLogger(SparqlExternalVocabularyService.class);
    
    private final Configuration config;
    private final VocabularyRepositoryService repositoryService;
    
    private final AccessControlListService aclService;
    private ApplicationEventPublisher eventPublisher;
    
    private static final String LIST_AVAILABLE_VOCABULARIES_QUERY = "import/listAvailableVocabularies.rq";
    private static final String EXPORT_FULL_VOCABULARY_QUERY = "import/exportFullVocabulary.rq";
    
        public SparqlExternalVocabularyService(VocabularyRepositoryService repositoryService,
                             AccessControlListService aclService,
                             Configuration config) {
        this.repositoryService = repositoryService;
        this.aclService = aclService;
        this.config = config;
    }
    
    

/**
 * Sends a SPARQL query to fetch list of available vocabularies.
 * 
 * @return list of available vocabulary information or null if connection failed
 */
    @Override
    public List<RdfsResource> getAvailableVocabularies() {

        List<RdfsResource> response;
        try {
            SPARQLRepository sparqlRepo = initSparqlRepository();

            try (RepositoryConnection conn = sparqlRepo.getConnection()) {
                String sparqlQuery = Utils.loadQuery(LIST_AVAILABLE_VOCABULARIES_QUERY);
                TupleQuery query = conn.prepareTupleQuery(sparqlQuery);

                TupleQueryResult result = query.evaluate();
                response = extractListOfAvailableVocabularies(result);

            } catch (QueryEvaluationException e) {
                LOG.error(e.getMessage());
                return List.of();
            }
            sparqlRepo.shutDown();
        } catch (RepositoryException ex) {
            return List.of();
        }
        return response;
    }

    private SPARQLRepository initSparqlRepository() throws RepositoryException {
        String sparqlEndpoint = config.getExternal().getResources();
        SPARQLRepository sparqlRepo = new SPARQLRepository(sparqlEndpoint);
        sparqlRepo.init();
        return sparqlRepo;
    }

    private List<RdfsResource> extractListOfAvailableVocabularies(final TupleQueryResult result) throws QueryEvaluationException {
        List<RdfsResource> response = new ArrayList<>();
        
        while (result.hasNext()) {
            BindingSet line = result.next();
            if (!line.hasBinding("slovnik")) {
                LOG.error("Error: no slovnik binding in: {}", line.toString());
                continue;
            }
            URI uri = URI.create(line.getBinding("slovnik").getValue().stringValue());
            HashMap<String, String> labels = new HashMap<>();
            
            // add cs label if available
            if (line.hasBinding("nazev_slovniku_cs")) {
                labels.put("cs", line.getBinding("nazev_slovniku_cs").getValue().stringValue());
            } else {
                labels.put("cs", uri.toString());
            }
            // add en label if available
            if (line.hasBinding("nazev_slovniku_en")) {
                labels.put("en", line.getBinding("nazev_slovniku_en").getValue().stringValue());
            } else {
                labels.put("en", uri.toString());
            }
            MultilingualString label = new MultilingualString(labels);
            response.add(new RdfsResource(uri, label, new MultilingualString(), ""));
        }
        return response;
    }

    /**
     * Imports multiple vocabularies from external source.
     *
     * @param vocabularyIris List of iris of vocabularies that shall be imported.
     * @return first imported Vocabulary
     */
    @Transactional
    @PreAuthorize("@vocabularyAuthorizationService.canCreate()")
    @Override
    public Vocabulary importFromExternalUris(List<String> vocabularyIris) {
        Vocabulary firstImportedVocabulary = null;

        for (String vocabularyIri : vocabularyIris) {
            try {
                LOG.trace("Starting import of external vocabulary {}", vocabularyIri);
                InputStream newVocabulary = downloadExternalVocabulary(vocabularyIri);
                if (newVocabulary != null) {
                    URI uri = URI.create(vocabularyIri);
                    Vocabulary vocabulary = repositoryService.importVocabulary(uri, RDFFormat.TURTLE.getDefaultMIMEType(), newVocabulary);

                    // add types
                    vocabulary.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_pouze_pro_cteni);
                    vocabulary.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_externi);

                    final AccessControlList acl = aclService.createFor(vocabulary);
                    vocabulary.setAcl(acl.getUri());

                    if (repositoryService.find(uri).isEmpty()) { // The vocabulary is new
                        eventPublisher.publishEvent(new VocabularyCreatedEvent(this, vocabulary.getUri()));
                    }

                    LOG.trace("Vocabulary {} import was successful.", vocabularyIri);

                    if (firstImportedVocabulary == null) {
                        firstImportedVocabulary = vocabulary;
                    }
                }
            } catch (QueryEvaluationException | RepositoryException ex) {
                LOG.error(ex.getMessage());
            }
        }
        
        return firstImportedVocabulary;
    }

    private InputStream downloadExternalVocabulary(String vocabularyIri) throws QueryEvaluationException, RepositoryException{
        
        SPARQLRepository sparqlRepo = initSparqlRepository();
        
        RepositoryConnection conn = sparqlRepo.getConnection();

        String sparqlQuery = String.format(Utils.loadQuery(EXPORT_FULL_VOCABULARY_QUERY), vocabularyIri);
        GraphQuery graphQuery = conn.prepareGraphQuery(sparqlQuery);

        GraphQueryResult result = graphQuery.evaluate();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Rio.write(result, outputStream, RDFFormat.TURTLE);
        InputStream vocabularyFile = new ByteArrayInputStream(outputStream.toByteArray());
        
        sparqlRepo.shutDown();
        
        return vocabularyFile;

    }
    
    @Scheduled(cron = "${termit.external.reloadCron:-}")
    @Transactional
    @Override
    public void reloadVocabularies() {
        LOG.debug("Reloading vocabularies at " + Instant.now());
        reloadAllExternal();
    }
    
    
    @Transactional
    public void reloadAllExternal(){
        List<String> externalVocabularies = repositoryService.findAll().stream()
                .filter((t) -> t.getTypes().contains(cz.cvut.kbss.termit.util.Vocabulary.s_c_externi))
                .map((t) -> t.getUri().toString())
                .toList();

        importFromExternalUris(externalVocabularies);
        
    }
    
    @Override
    public void setApplicationEventPublisher(@Nonnull ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
}
