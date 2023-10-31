/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.export;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Exports vocabulary glossary in a SKOS-compatible format serialized as Turtle.
 */
@Service("skos-turtle")
public class SKOSTurtleVocabularyExporter extends SKOSVocabularyExporter {

    @Autowired
    public SKOSTurtleVocabularyExporter(ApplicationContext context) {
        super(context);
    }

    @Override
    protected ExportFormat exportFormat() {
        return ExportFormat.TURTLE;
    }
}
