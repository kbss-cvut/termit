package cz.cvut.kbss.termit.persistence.dao.util;

import java.net.URI;

public record Quad(URI subject, URI predicate, Object object, URI context) {
}
