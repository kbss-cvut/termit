package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.AggregatedChangeInfo;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.exception.AssetRemovalException;
import cz.cvut.kbss.termit.exception.VocabularyImportException;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Model;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.dao.AssetDao;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSImporter;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Validator;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@CacheConfig(cacheNames = "vocabularies")
@Service
public class VocabularyRepositoryService extends BaseAssetRepositoryService<Vocabulary> implements VocabularyService {

    private static final Logger LOG = LoggerFactory.getLogger(VocabularyRepositoryService.class);

    private final IdentifierResolver idResolver;

    private final VocabularyDao vocabularyDao;

    private final TermService termService;

    private final ChangeRecordService changeRecordService;

    private final Configuration config;

    private final ApplicationContext context;

    @Autowired
    public VocabularyRepositoryService(ApplicationContext context, VocabularyDao vocabularyDao,
                                       IdentifierResolver idResolver,
                                       Validator validator, ChangeRecordService changeRecordService,
                                       @Lazy TermService termService, final Configuration config) {
        super(validator);
        this.context = context;
        this.vocabularyDao = vocabularyDao;
        this.idResolver = idResolver;
        this.termService = termService;
        this.changeRecordService = changeRecordService;
        this.config = config;
    }

    /**
     * This method ensures new instances of the prototype-scoped bean are returned on every call.
     */
    private SKOSImporter getSKOSImporter() {
        return context.getBean(SKOSImporter.class);
    }

    @Override
    protected AssetDao<Vocabulary> getPrimaryDao() {
        return vocabularyDao;
    }

    @Cacheable
    @Override
    public List<Vocabulary> findAll() {
        return super.findAll();
    }

    @CacheEvict(allEntries = true)
    @Override
    public void persist(Vocabulary instance) {
        super.persist(instance);
    }

    @Override
    protected void prePersist(Vocabulary instance) {
        super.prePersist(instance);
        if (instance.getUri() == null) {
            instance.setUri(idResolver.generateIdentifier(config.getNamespace().getVocabulary(),
                                                          instance.getLabel()));
        }
        verifyIdentifierUnique(instance);
        initGlossaryAndModel(instance);
        if (instance.getDocument() != null) {
            instance.getDocument().setVocabulary(null);
        }
    }

    private void initGlossaryAndModel(Vocabulary vocabulary) {
        final String iriBase = vocabulary.getUri().toString();
        if (vocabulary.getGlossary() == null) {
            vocabulary.setGlossary(new Glossary());
            vocabulary.getGlossary().setUri(idResolver.generateIdentifier(iriBase, config.getGlossary().getFragment()));
        }
        if (vocabulary.getModel() == null) {
            vocabulary.setModel(new Model());
            vocabulary.getModel().setUri(idResolver.generateIdentifier(iriBase, Constants.DEFAULT_MODEL_IRI_COMPONENT));
        }
    }

    @Override
    protected void preUpdate(Vocabulary instance) {
        super.preUpdate(instance);
        verifyVocabularyImports(instance);
    }

    @CacheEvict(allEntries = true)
    @Override
    @Transactional
    public Vocabulary update(Vocabulary instance) {
        return super.update(instance);
    }

    /**
     * Ensures that possible vocabulary import removals are not prevented by existing inter-vocabulary term
     * relationships (terms from the updated vocabulary having parents from vocabularies whose import has been
     * removed).
     */
    private void verifyVocabularyImports(Vocabulary update) {
        final Vocabulary original = findRequired(update.getUri());
        final Set<URI> removedImports = new HashSet<>(Utils.emptyIfNull(original.getImportedVocabularies()));
        removedImports.removeAll(Utils.emptyIfNull(update.getImportedVocabularies()));
        final Set<URI> invalid = removedImports.stream().filter(ri -> vocabularyDao
                .hasInterVocabularyTermRelationships(update.getUri(), ri)).collect(
                Collectors.toSet());
        if (!invalid.isEmpty()) {
            throw new VocabularyImportException("Cannot remove imports of vocabularies " + invalid +
                                                        ", there are still relationships between terms.",
                                                "error.vocabulary.update.imports.danglingTermReferences");
        }
    }

    @Override
    public Collection<URI> getTransitivelyImportedVocabularies(Vocabulary entity) {
        return vocabularyDao.getTransitivelyImportedVocabularies(entity);
    }

    @Override
    public List<AbstractChangeRecord> getChanges(Vocabulary asset) {
        return changeRecordService.getChanges(asset);
    }

    @Transactional(readOnly = true)
    @Override
    public List<AggregatedChangeInfo> getChangesOfContent(Vocabulary vocabulary) {
        return vocabularyDao.getChangesOfContent(vocabulary);
    }

    @CacheEvict(allEntries = true)
    @Transactional
    @Override
    public Vocabulary importVocabulary(boolean rename, URI vocabularyIri, MultipartFile file) {
        Objects.requireNonNull(file);
        try {
            Metadata metadata = new Metadata();
            metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, file.getName());
            metadata.add(Metadata.CONTENT_TYPE, file.getContentType());
            String contentType = new Tika().detect(file.getInputStream(), metadata);
            return getSKOSImporter().importVocabulary(rename,
                                                      vocabularyIri,
                                                      contentType,
                                                      this::persist,
                                                      file.getInputStream()
            );
        } catch (VocabularyImportException e) {
            throw e;
        } catch (Exception e) {
            throw new VocabularyImportException("Unable to import vocabulary, because of: " + e.getMessage());
        }
    }

    @Override
    public long getLastModified() {
        return vocabularyDao.getLastModified();
    }

    @CacheEvict(allEntries = true)
    @Override
    public void remove(Vocabulary instance) {
        if (instance.getDocument() != null) {
            throw new AssetRemovalException("Removal of document vocabularies is not supported yet.");
        }

        final List<Vocabulary> vocabularies = vocabularyDao.getImportingVocabularies(instance);
        if (!vocabularies.isEmpty()) {
            throw new AssetRemovalException(
                    "Vocabulary cannot be removed. It is referenced from other vocabularies: "
                            + vocabularies.stream().map(Vocabulary::getLabel).collect(Collectors.joining(", ")));
        }

        if (!termService.isEmpty(instance)) {
            throw new AssetRemovalException("Vocabulary cannot be removed. It contains terms.");
        }

        super.remove(instance);
    }

    @Override
    @Async
    public void runTextAnalysisOnAllTerms(Vocabulary vocabulary) {
        LOG.debug("Analyzing definitions of all terms in vocabulary {} and vocabularies it imports.", vocabulary);
        final List<TermDto> allTerms = termService.findAll(vocabulary);
        getTransitivelyImportedVocabularies(vocabulary).forEach(
                importedVocabulary -> allTerms.addAll(termService.findAll(getRequiredReference(importedVocabulary))));
        allTerms.stream().filter(t -> t.getDefinition() != null)
                .forEach(t -> termService.analyzeTermDefinition(t, vocabulary.getUri()));
    }

    @Override
    @Async
    public void runTextAnalysisOnAllVocabularies() {
        vocabularyDao.findAll().forEach(v -> {
            List<TermDto> terms = termService.findAll(v);
            terms.stream().filter(t -> t.getDefinition() != null)
                 .forEach(t -> termService.analyzeTermDefinition(t, v.getUri()));
        });
    }

    @Override
    public List<ValidationResult> validateContents(Vocabulary instance) {
        return vocabularyDao.validateContents(instance);
    }

    @Override
    public Integer getTermCount(Vocabulary vocabulary) {
        return vocabularyDao.getTermCount(vocabulary);
    }
}
