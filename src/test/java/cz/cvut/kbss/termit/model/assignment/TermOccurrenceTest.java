package cz.cvut.kbss.termit.model.assignment;

import cz.cvut.kbss.termit.environment.Generator;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;

class TermOccurrenceTest {

    @Test
    void resolveContextCreatesDescriptorWithContextBasedOnSpecifiedSource() {
        final URI source = Generator.generateFileWithId("test.html").getUri();

        final URI ctx = TermOccurrence.resolveContext(source);
        assertThat(ctx.toString(), startsWith(source.toString()));
        assertThat(ctx.toString(), endsWith(TermOccurrence.CONTEXT_SUFFIX));
    }

    @Test
    void resolveContextSupportsSourceIdentifiersEndingWithSlash() {
        final URI source = URI.create(Generator.generateUri() + "/");
        final URI ctx = TermOccurrence.resolveContext(source);
        assertThat(ctx.toString(), startsWith(source.toString()));
        assertThat(ctx.toString(), endsWith("/" + TermOccurrence.CONTEXT_SUFFIX));
    }
}
