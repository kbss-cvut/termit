package cz.cvut.kbss.termit;

import cz.cvut.kbss.termit.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Collections;

@SpringBootApplication
public class TermItApplication {

    private static final Logger LOG = LoggerFactory.getLogger(TermItApplication.class);

    public static void main(String[] args) {
        printStartupMessage();
        SpringApplication.run(TermItApplication.class, args);
    }

    private static void printStartupMessage() {
        final String msg = "* TermIt " + Constants.VERSION + " *";
        LOG.info(String.join("", Collections.nCopies(msg.length(), "*")));
        LOG.info(msg);
        LOG.info(String.join("", Collections.nCopies(msg.length(), "*")));
    }
}
