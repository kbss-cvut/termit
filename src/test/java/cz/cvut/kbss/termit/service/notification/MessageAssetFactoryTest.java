package cz.cvut.kbss.termit.service.notification;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.mail.ApplicationLinkBuilder;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.geom.GeneralPath;
import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageAssetFactoryTest {

    @Mock
    private ApplicationLinkBuilder linkBuilder;

    @Mock
    private DataRepositoryService dataService;

    @InjectMocks
    private MessageAssetFactory sut;

    @Test
    void createUsesVocabularyTitleAndLinkBuilderGeneratedLinkToConstructMessageAsset() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(linkBuilder.linkTo(vocabulary)).thenReturn(vocabulary.getUri().toString());

        final MessageAssetFactory.MessageAsset result = sut.create(vocabulary);
        assertEquals(vocabulary.getLabel(), result.getLabel());
        assertEquals(vocabulary.getUri().toString(), result.getLink());
    }

    @Test
    void createAddsTermVocabularyLabelInParenthesesAsMessageAssetLabel() throws Exception {
        final String vocabularyLabel = "Vocabulary " + Generator.randomInt(0, 1000);
        final Term term = Generator.generateTermWithId();
        when(linkBuilder.linkTo(term)).thenReturn(term.getUri().toString());
        when(dataService.getLabel(term.getVocabulary())).thenReturn(Optional.of(vocabularyLabel));
        // Simulate autowired configuration
        final Field configField = Term.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(term, new Configuration());

        final MessageAssetFactory.MessageAsset result = sut.create(term);
        assertEquals(term.getPrimaryLabel() + " (" + vocabularyLabel + ")", result.getLabel());
        assertEquals(term.getUri().toString(), result.getLink());
    }
}
