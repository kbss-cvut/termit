/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        configuration.getWorkspace().setAllVocabulariesEditable(true);
        this.sut = new EditableVocabularies(configuration, appContext.getBeanProvider(EditableVocabulariesHolder.class));
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        assertTrue(sut.isEditable(vocabulary));
    }

    @Test
    void isEditableReturnsFalseForUnregisteredVocabularyWhenAllVocabulariesAreNotEditable() {
        configuration.getWorkspace().setAllVocabulariesEditable(false);
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
