package cz.cvut.kbss.termit.service.init.lucene;

import tools.jackson.databind.JsonNode;

import java.net.URI;

public record LuceneConnector(URI uri, JsonNode options) {
}
