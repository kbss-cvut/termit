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
package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.SelectorGenerationException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.assignment.DefinitionalOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.OccurrenceTarget;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.selector.Selector;
import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import cz.cvut.kbss.termit.service.business.ResourceService;
import cz.cvut.kbss.termit.service.document.html.HtmlSelectorGenerators;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermOccurrenceSelectorCreatorTest {

    private static final String CONTENT = """
                <html>
                <body>
                    <div>
                        Text before the element, <span about="_:elem">test</span>, and text after the element.
                    </div>
                </body>
            </html>
            """;

    @Mock
    private HtmlSelectorGenerators selectorGenerators;

    @Mock
    private DocumentManager documentManager;

    @Mock
    private ResourceService resourceService;

    @InjectMocks
    private TermOccurrenceSelectorCreator sut;

    @Test
    void createSelectorsThrowsUnsupportedOperationExceptionWhenTargetIsTermDefinitionalTarget() {
        final OccurrenceTarget target = new DefinitionalOccurrenceTarget(Generator.generateTermWithId());
        assertThrows(UnsupportedOperationException.class, () -> sut.createSelectors(target, "someId"));
        verify(selectorGenerators, never()).generateSelectors(any());
    }

    @Test
    void createSelectorsLoadsTargetFileFindsOccurrenceElementAndGeneratesSelectorsForIt() {
        final File f = Generator.generateFileWithId("test.html");
        final OccurrenceTarget target = new FileOccurrenceTarget(f);
        when(resourceService.findRequired(f.getUri())).thenReturn(f);
        when(documentManager.loadFileContent(f)).thenReturn(CONTENT);
        final Set<Selector> selectors = Set.of(new TextQuoteSelector("test", "element, ", ", and text after"));
        when(selectorGenerators.generateSelectors(any())).thenReturn(selectors);

        final Set<Selector> result = sut.createSelectors(target, "elem");
        assertEquals(selectors, result);
        verify(documentManager).loadFileContent(f);
        final Document doc = Jsoup.parse(CONTENT);
        Elements elem = doc.body().select("[about=_:elem]");
        final ArgumentCaptor<Element[]> captor = ArgumentCaptor.forClass(Element[].class);
        verify(selectorGenerators).generateSelectors(captor.capture());
        // equals does not work for elements from two documents loaded from the same content
        assertEquals(elem.get(0).wholeText(), captor.getValue()[0].wholeText());
        assertEquals(elem.get(0).attr("id"), captor.getValue()[0].attr("id"));
    }

    @Test
    void createSelectorsThrowsSelectorGenerationExceptionWhenElementIsNotFound() {
        final File f = Generator.generateFileWithId("test.html");
        final OccurrenceTarget target = new FileOccurrenceTarget(f);
        when(resourceService.findRequired(f.getUri())).thenReturn(f);
        when(documentManager.loadFileContent(f)).thenReturn(CONTENT);

        assertThrows(SelectorGenerationException.class, () -> sut.createSelectors(target, "unknownId"));
        verify(selectorGenerators, never()).generateSelectors(any());
    }
}
