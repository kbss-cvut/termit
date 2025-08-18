package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.event.VocabularyCreatedEvent;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import jakarta.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic concerning external vocabularies.
 */
@Service
public class ExternalVocabularyService implements ApplicationEventPublisherAware{
    
    private static final Logger LOG = LoggerFactory.getLogger(VocabularyService.class);
    
    private final ApplicationContext context;
    private final VocabularyRepositoryService repositoryService;
    private final VocabularyService vocabularyService;
    
    private final AccessControlListService aclService;
    private ApplicationEventPublisher eventPublisher;
    
    private static final String LIST_AVAILABLE_VOCABULARIES_QUERY = "import/listAvailableVocabularies.rq";
    private static final String EXPORT_FULL_VOCABULARY_QUERY = "import/exportFullVocabulary.rq";
    
        public ExternalVocabularyService(VocabularyRepositoryService repositoryService,
                             VocabularyService vocabularyService,
                             AccessControlListService aclService,
                             ApplicationContext context) {
        this.repositoryService = repositoryService;
        this.vocabularyService = vocabularyService;
        this.aclService = aclService;
        this.context = context;
    }
    
    

/**
 * Sends a SPARQL query to fetch list of available vocabularies.
 * 
 * @return list of available vocabulary information or null if connection failed
 */
    public List<RdfsResource> getAvailableVocabularies() {

        List<RdfsResource> response = new ArrayList<>();
        
        final Configuration config = context.getBean(Configuration.class);

        String sparqlEndpoint = config.getExternal().getResources();
        String sparqlQuery = Utils.loadQuery(LIST_AVAILABLE_VOCABULARIES_QUERY);

        SPARQLRepository sparqlRepo = new SPARQLRepository(sparqlEndpoint);
        sparqlRepo.init();
        try (RepositoryConnection conn = sparqlRepo.getConnection()) {
            TupleQuery query = conn.prepareTupleQuery(sparqlQuery);

            try (TupleQueryResult result = query.evaluate()) {
                response = extractListOfAvailableVocabularies(result);
            }
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
        return response;
    }

    private List<RdfsResource> extractListOfAvailableVocabularies(final TupleQueryResult result) throws URISyntaxException, QueryEvaluationException {
        List<RdfsResource> response = new ArrayList<>();
        
        while (result.hasNext()) {
            BindingSet line = result.next();
            if (!line.hasBinding("slovnik")) {
                LOG.error("Error: no slovnik binding in: {}", line.toString());
                continue;
            }
            URI uri = new URI(line.getBinding("slovnik").getValue().stringValue());
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
     * @param vocabularyIris List of
     * @return first imported Vocabulary
     * @throws URISyntaxException
     * @throws QueryEvaluationException
     * @throws RepositoryException
     */
    @Transactional
    @PreAuthorize("@vocabularyAuthorizationService.canCreate()")
    public Vocabulary importFromExternalUris(List<String> vocabularyIris) throws URISyntaxException, QueryEvaluationException, RepositoryException {
        Vocabulary firstImportedVocabulary = null;
        
        for (String vocabularyIri : vocabularyIris) {
            InputStream newVocabulary = downloadExternalVocabulary(vocabularyIri);
            if (newVocabulary != null) {
                URI uri = new URI(vocabularyIri);
                Vocabulary vocabulary = null;
                Optional<Vocabulary> oldVocabulary = repositoryService.find(uri);
                if (oldVocabulary.isEmpty()) { // new vocabulary  
                    vocabulary = repositoryService.importVocabulary(uri, RDFFormat.TURTLE.getDefaultMIMEType(), newVocabulary);

                    // add types
                    vocabulary.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_pouze_pro_cteni);
                    vocabulary.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_externi);

                    final AccessControlList acl = aclService.createFor(vocabulary);
                    vocabulary.setAcl(acl.getUri());

                    eventPublisher.publishEvent(new VocabularyCreatedEvent(this, vocabulary.getUri()));
                    LOG.debug("Vocabulary {} import was successful.", vocabularyIri);
                } else { // updating existing vocabulary
                    vocabulary = repositoryService.importVocabulary(uri, RDFFormat.TURTLE.getDefaultMIMEType(), newVocabulary);
                    
                    // add types
                    vocabulary.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_pouze_pro_cteni);
                    vocabulary.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_externi);

                    final AccessControlList acl = aclService.createFor(vocabulary);
                    vocabulary.setAcl(acl.getUri());
                    
                    LOG.debug("Vocabulary {} import was successful.", vocabularyIri);
                }
                
                if (firstImportedVocabulary == null) {
                    firstImportedVocabulary = vocabulary;
                }
            }
        }
        
        return firstImportedVocabulary;
    }

    @Transactional
    private InputStream downloadExternalVocabulary(String vocabularyIri) throws QueryEvaluationException, RepositoryException{

        List<RdfsResource> response = new ArrayList<>();
        final Configuration config = context.getBean(Configuration.class);

        String sparqlEndpoint = config.getExternal().getResources();
        String sparqlQuery = String.format(Utils.loadQuery(EXPORT_FULL_VOCABULARY_QUERY), vocabularyIri);

        SPARQLRepository sparqlRepo = new SPARQLRepository(sparqlEndpoint);
        sparqlRepo.init();
        RepositoryConnection conn = sparqlRepo.getConnection();
        GraphQuery graphQuery = conn.prepareGraphQuery(sparqlQuery);

        GraphQueryResult result = graphQuery.evaluate();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Rio.write(result, outputStream, RDFFormat.TURTLE);
        InputStream vocabularyFile = new ByteArrayInputStream(outputStream.toByteArray());
        return vocabularyFile;

    }
    
    @Scheduled(cron = "${termit.external.reloadCron:-}")
    @Transactional
    public void reloadVocabularies() throws URISyntaxException {
        LOG.debug("Reloading vocabularies at " + Instant.now());
        reloadAllExternal();
    }
    
    
    @Transactional
    public void reloadAllExternal() throws URISyntaxException{
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
