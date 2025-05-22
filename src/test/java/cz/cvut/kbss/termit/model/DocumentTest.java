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
package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentTest {

    @Test
    void getFileDirectoryNameReturnsNameBasedOnNormalizedNameAndUriHash() {
        final Document document = new Document();
        document.setLabel("Metropolitan plan");
        document.setUri(URI.create("http://onto.fel.cvut.cz/ontologies/termit/vocabulary/metropolitan-plan"));
        final String result = document.getDirectoryName();
        assertNotNull(result);
        assertThat(result, startsWith(IdentifierResolver.normalizeToAscii(document.getLabel())));
    }

    @Test
    void getFileDirectoryNameThrowsIllegalStateWhenNameIsMissing() {
        final Document document = new Document();
        document.setUri(URI.create("http://onto.fel.cvut.cz/ontologies/termit/vocabulary/metropolitan-plan"));
        assertThrows(IllegalStateException.class, document::getDirectoryName);
    }

    @Test
    void getFileDirectoryNameThrowsIllegalStateWhenUriIsMissing() {
        final Document document = new Document();
        document.setLabel("Metropolitan plan");
        assertThrows(IllegalStateException.class, document::getDirectoryName);
    }

    @Test
    void getFileReturnsOptionalWithFileWithMatchingName() {
        final Document document = new Document();
        document.setLabel("Metropolitan plan");
        final File fOne = new File();
        fOne.setLabel("test1.html");
        document.addFile(fOne);
        final File fTwo = new File();
        fTwo.setLabel("test2.html");
        document.addFile(fTwo);
        final Optional<File> result = document.getFile(fOne.getLabel());
        assertTrue(result.isPresent());
        assertSame(fOne, result.get());
    }

    @Test
    void getFileReturnsEmptyOptionalForUnknownFileName() {
        final Document document = new Document();
        document.setLabel("Metropolitan plan");
        final File fOne = new File();
        fOne.setLabel("test1.html");
        document.addFile(fOne);
        assertFalse(document.getFile("unknown.html").isPresent());
    }

    @Test
    void getFileReturnsEmptyOptionalForDocumentWithoutFiles() {
        final Document document = new Document();
        document.setLabel("Metropolitan plan");
        assertFalse(document.getFile("unknown.html").isPresent());
    }
}
