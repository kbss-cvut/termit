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
package cz.cvut.kbss.termit.model.resource;

import cz.cvut.kbss.termit.environment.Generator;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileTest {

    @Test
    void getDirectoryNameReturnsParentDocumentDirectoryWhenDocumentIsReferenced() {
        final File sut = new File();
        sut.setLabel("text-mpp.html");
        sut.setUri(Generator.generateUri());
        final Document parent = new Document();
        parent.setUri(Generator.generateUri());
        parent.setLabel("Parent document");
        sut.setDocument(parent);
        parent.addFile(sut);
        assertEquals(parent.getDirectoryName(), sut.getDirectoryName());
    }

    @Test
    void getDirectoryNameReturnsDirectoryNameDerivedFromFileNameWhenParentDocumentIsNotSet() {
        final File sut = new File();
        sut.setLabel("text-mpp.html");
        sut.setUri(Generator.generateUri());
        final String result = sut.getDirectoryName();
        assertThat(result, containsString(sut.getLabel().substring(0, sut.getLabel().indexOf('.'))));
        assertThat(result, containsString(Integer.toString(sut.getUri().hashCode())));
    }

    @Test
    void getDirectoryNameDoesNotContainFileExtension() {
        final File sut = new File();
        sut.setLabel("text-mpp.html");
        sut.setUri(Generator.generateUri());
        final String result = sut.getDirectoryName();
        assertThat(result, not(containsString(".html")));
    }

    @Test
    void getDirectoryNameThrowsIllegalStateExceptionWhenFileLabelIsMissing() {
        final File sut = new File();
        sut.setUri(Generator.generateUri());
        assertThrows(IllegalStateException.class, sut::getDirectoryName);
    }

    @Test
    void getDirectoryNameThrowsIllegalStateExceptionWhenFileUriIsMissing() {
        final File sut = new File();
        sut.setLabel("text-mpp.html");
        assertThrows(IllegalStateException.class, sut::getDirectoryName);
    }

    @Test
    void getDirectoryNameSupportsFileLabelWithoutExtension() {
        final File sut = new File();
        sut.setLabel("text-mpp");
        sut.setUri(Generator.generateUri());
        final String result = sut.getDirectoryName();
        assertThat(result, containsString(sut.getLabel()));
        assertThat(result, containsString(Integer.toString(sut.getUri().hashCode())));
    }
}