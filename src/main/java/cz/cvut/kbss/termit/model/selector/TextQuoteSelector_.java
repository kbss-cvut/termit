package cz.cvut.kbss.termit.model.selector;

import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;

@StaticMetamodel(TextQuoteSelector.class)
public abstract class TextQuoteSelector_ extends Selector_ {

    public static volatile SingularAttribute<TextQuoteSelector, String> exactMatch;

    public static volatile SingularAttribute<TextQuoteSelector, String> prefix;

    public static volatile SingularAttribute<TextQuoteSelector, String> suffix;
}
