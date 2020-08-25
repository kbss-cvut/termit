package cz.cvut.kbss.termit.util;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;

public class LanguageUtils {

    private String repo = "https://xn--slovnk-7va.gov.cz/sparql";

    public void generateBasicLanguage() throws IOException {
        final URL url = getClass().getResource("/query/language.rq");
        final Query q = QueryFactory.read(url.toString());
        final Model m = QueryExecutionFactory.sparqlService(repo, q).execConstruct();
        m.setNsPrefixes(q.getPrefixMapping());
        m.write(new FileWriter("src/main/resources/language.ttl"),"TURTLE", null);
    }

    public static void main(String[] args) throws IOException {
        new LanguageUtils().generateBasicLanguage();
    }
}
