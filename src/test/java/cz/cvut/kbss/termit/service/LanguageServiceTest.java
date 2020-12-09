/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service;

import cz.cvut.kbss.termit.exception.CannotFetchTypesException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.service.language.LanguageServiceJena;
import java.io.IOException;
import java.net.URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import org.springframework.core.io.ClassPathResource;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;


class LanguageServiceTest extends BaseServiceTestRunner {

    @Mock
    private ClassPathResource languageTtlUrl;

    @InjectMocks
    private LanguageServiceJena sut;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void getTypesForBasicLanguage() throws IOException {
        final URL url = ClassLoader.getSystemResource("languages/language.ttl");
        when(languageTtlUrl.getURL()).thenReturn(url);
        List<Term> result = sut.getTypes();
        assertEquals(10, result.size());
    }

    @Test
    void getLeafTypesForBasicLanguage() throws IOException {
        final URL url = ClassLoader.getSystemResource("languages/language.ttl");
        when(languageTtlUrl.getURL()).thenReturn(url);
        List<Term> result = sut.getLeafTypes();
        assertEquals(8, result.size());
    }

    @Test
    void getTypesThrowsCannotFetchTypesException() {
        assertThrows(CannotFetchTypesException.class,
            () -> sut.getTypes());
    }
}
