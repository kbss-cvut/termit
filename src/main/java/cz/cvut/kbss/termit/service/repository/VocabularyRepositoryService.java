package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.exception.VocabularyRemovalException;
import cz.cvut.kbss.termit.model.*;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.dao.AssetDao;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.service.business.WorkspaceService;
import cz.cvut.kbss.termit.service.importer.VocabularyImportService;
import cz.cvut.kbss.termit.util.ConfigParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Validator;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class VocabularyRepositoryService extends BaseAssetRepositoryService<Vocabulary> implements VocabularyService {

    private final IdentifierResolver idResolver;

    private final VocabularyDao vocabularyDao;

    private final TermService termService;

    private final ChangeRecordService changeRecordService;

    private final VocabularyImportService importService;

    private final WorkspaceService workspaceService;

    @Autowired
    public VocabularyRepositoryService(VocabularyDao vocabularyDao, IdentifierResolver idResolver,
                                       Validator validator, ChangeRecordService changeRecordService,
                                       @Lazy TermService termService, VocabularyImportService importService,
                                       WorkspaceService workspaceService) {
        super(validator);
        this.vocabularyDao = vocabularyDao;
        this.idResolver = idResolver;
        this.termService = termService;
        this.changeRecordService = changeRecordService;
        this.importService = importService;
        this.workspaceService = workspaceService;
    }

    @Override
    protected AssetDao<Vocabulary> getPrimaryDao() {
        return vocabularyDao;
    }

    @Override
    public List<Vocabulary> findAll() {
        final List<Vocabulary> loaded = vocabularyDao.findAll(workspaceService.getCurrentWorkspace());
        return loaded.stream().map(this::postLoad).collect(Collectors.toList());
    }

    @Override
    protected void prePersist(@NonNull Vocabulary instance) {
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
    }

    @Override
    public Collection<URI> getTransitiveDependencies(Vocabulary entity) {
        return vocabularyDao.getTransitiveDependencies(entity);
    }

    @Override
    public List<AbstractChangeRecord> getChanges(Vocabulary asset) {
        return changeRecordService.getChanges(asset);
    }

    @Override
    public List<AbstractChangeRecord> getChangesOfContent(Vocabulary asset) {
        final List<AbstractChangeRecord> changes = new ArrayList<>();
        for (final Term term : termService.findAll(asset)) {
            changes.addAll(termService.getChanges(term));
        }
        return changes;
    }

    @Override
    public Vocabulary importVocabulary(MultipartFile file) {
        Objects.requireNonNull(file);
        return importService.importVocabulary(file);
    }

    @Override
    public long getLastModified() {
        return vocabularyDao.getLastModified();
    }

    @Override
    public void remove(Vocabulary instance) {
        if (instance instanceof DocumentVocabulary) {
            throw new VocabularyRemovalException(
                    "Removal of document vocabularies is not supported yet.");
        }

        final List<Vocabulary> vocabularies = vocabularyDao.getDependentVocabularies(instance);
        if (!vocabularies.isEmpty()) {
            throw new VocabularyRemovalException(
                    "Vocabulary cannot be removed. It is referenced from other vocabularies: "
                            + vocabularies.stream().map(Vocabulary::getLabel).collect(
                            Collectors.joining(", ")));
        }

        if (!termService.isEmpty(instance)) {
            throw new VocabularyRemovalException(
                    "Vocabulary cannot be removed. It contains terms.");
        }

        super.remove(instance);
    }

    @Transactional
    @Override
    public List<ValidationResult> validateContents(Vocabulary instance) {
        return vocabularyDao.validateContents(instance, workspaceService.getCurrentWorkspace());
    }
}
