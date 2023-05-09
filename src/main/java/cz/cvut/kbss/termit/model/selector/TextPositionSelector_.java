package cz.cvut.kbss.termit.model.selector;

import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;

@StaticMetamodel(TextPositionSelector.class)
public abstract class TextPositionSelector_ extends Selector_ {

    public static volatile SingularAttribute<TextPositionSelector, Integer> start;

    public static volatile SingularAttribute<TextPositionSelector, Integer> end;
}
