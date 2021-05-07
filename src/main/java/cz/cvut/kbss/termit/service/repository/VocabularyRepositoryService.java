package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.exception.VocabularyImportException;
import cz.cvut.kbss.termit.exception.VocabularyRemovalException;
import cz.cvut.kbss.termit.model.*;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.dao.AssetDao;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.service.importer.VocabularyImportService;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Validator;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VocabularyRepositoryService extends BaseAssetRepositoryService<Vocabulary> implements VocabularyService {

    private final IdentifierResolver idResolver;

    private final VocabularyDao vocabularyDao;

    private final TermService termService;

    private final ChangeRecordService changeRecordService;

    final VocabularyImportService importService;

    final ResourceRepositoryService resourceService;

    @Autowired
    public VocabularyRepositoryService(VocabularyDao vocabularyDao, IdentifierResolver idResolver,
                                       Validator validator, ChangeRecordService changeRecordService,
                                       @Lazy TermService termService, VocabularyImportService importService,
                                       @Lazy ResourceRepositoryService resourceService) {
        super(validator);
        this.vocabularyDao = vocabularyDao;
        this.idResolver = idResolver;
        this.termService = termService;
        this.changeRecordService = changeRecordService;
        this.importService = importService;
        this.resourceService = resourceService;
    }

    @Override
    protected AssetDao<Vocabulary> getPrimaryDao() {
        return vocabularyDao;
    }

    @Override
    protected void prePersist(Vocabulary instance) {
        super.prePersist(instance);
        if (instance.getUri() == null) {
            instance.setUri(idResolver.generateIdentifier(ConfigParam.NAMESPACE_VOCABULARY,
                instance.getLabel()));
        }
        verifyIdentifierUnique(instance);
        if (instance.getGlossary() == null) {
            instance.setGlossary(new Glossary());
        }
        if (instance.getModel() == null) {
            instance.setModel(new Model());
        }
        if (instance.getDocument() != null) {
            instance.getDocument().setVocabulary(null);
        }
    }

    @Override
    protected void preUpdate(Vocabulary instance) {
        super.preUpdate(instance);
        verifyVocabularyImports(instance);
    }

    @Override
    @Transactional
    public Vocabulary update(Vocabulary vNew) {
        final Vocabulary vOriginal = super.findRequired(vNew.getUri());
        resourceService.rewireDocumentsOnVocabularyUpdate(vOriginal, vNew);
        super.update(vNew);
        return vNew;
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

    @Override
    public List<AbstractChangeRecord> getChangesOfContent(Vocabulary asset) {
        return vocabularyDao.getChangesOfContent(asset);
    }

    @Override
    public Vocabulary importVocabulary(String vocabularyIri, MultipartFile file) {
        Objects.requireNonNull(file);
        return importService.importVocabulary(vocabularyIri, file);
    }

    @Override
    public long getLastModified() {
        return vocabularyDao.getLastModified();
    }

    @Override
    public void remove(Vocabulary instance) {
        if (instance.getDocument() != null) {
            throw new VocabularyRemovalException(
                    "Removal of document vocabularies is not supported yet.");
        }

        final List<Vocabulary> vocabularies = vocabularyDao.getImportingVocabularies(instance);
        if (!vocabularies.isEmpty()) {
            throw new VocabularyRemovalException(
                    "Vocabulary cannot be removed. It is referenced from other vocabularies: "
                            + vocabularies.stream().map(Vocabulary::getLabel).collect(
                            Collectors.joining(", ")));
        }

        if (!termService.isEmpty(instance)) {
            throw new VocabularyRemovalException("Vocabulary cannot be removed. It contains terms.");
        }

        super.remove(instance);
    }

    @Override
    public List<ValidationResult> validateContents(Vocabulary instance) {
        return vocabularyDao.validateContents(instance);
    }
}
