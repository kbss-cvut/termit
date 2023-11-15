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
package cz.cvut.kbss.termit.aspect;

import cz.cvut.kbss.termit.event.VocabularyContentModified;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

@Aspect
public class VocabularyContentModificationAspect {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Pointcut("@annotation(cz.cvut.kbss.termit.asset.provenance.ModifiesData) && target(cz.cvut.kbss.termit.persistence.dao.TermDao)")
    public void vocabularyContentModificationOperation() {
    }

    @After("vocabularyContentModificationOperation()")
    public void vocabularyContentModified() {
        eventPublisher.publishEvent(new VocabularyContentModified(this));
    }
}
