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
