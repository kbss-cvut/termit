package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EditableVocabulariesTest {

    private Configuration configuration;

    private ApplicationContext appContext;

    private EditableVocabularies sut;

    @BeforeEach
    void setUp() {
        this.configuration = new Configuration();
        final AnnotationConfigWebApplicationContext appCtx = new AnnotationConfigWebApplicationContext();
        appCtx.register(EditableVocabulariesHolder.class);
        appCtx.refresh();
        this.appContext = appCtx;
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        this.sut = new EditableVocabularies(configuration, appCtx.getBeanProvider(EditableVocabulariesHolder.class));
    }

    @Test
    void isEditableReturnsTrueForVocabularyRegisteredAsEditable() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final URI context = Generator.generateUri();
        sut.registerEditableVocabulary(vocabulary.getUri(), context);

        assertTrue(sut.isEditable(vocabulary));
    }

    @Test
    void isEditableReturnsTrueForUnregisteredVocabularyWhenAllVocabulariesAreEditable() {
        configuration.setWorkspace(new Configuration.Workspace(true));
        this.sut = new EditableVocabularies(configuration, appContext.getBeanProvider(EditableVocabulariesHolder.class));
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        assertTrue(sut.isEditable(vocabulary));
    }

    @Test
    void isEditableReturnsFalseForUnregisteredVocabularyWhenAllVocabulariesAreNotEditable() {
        configuration.setWorkspace(new Configuration.Workspace(false));
        this.sut = new EditableVocabularies(configuration, appContext.getBeanProvider(EditableVocabulariesHolder.class));
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        assertFalse(sut.isEditable(vocabulary));
    }

    @Test
    void clearRemovesPreviouslyRegisteredContexts() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final URI context = Generator.generateUri();
        sut.registerEditableVocabulary(vocabulary.getUri(), context);

        assertEquals(Set.of(context), sut.getRegisteredContexts());
        sut.clear();
        assertTrue(sut.getRegisteredContexts().isEmpty());
    }
}
