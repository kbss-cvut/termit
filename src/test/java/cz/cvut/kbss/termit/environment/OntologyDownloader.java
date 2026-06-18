package cz.cvut.kbss.termit.environment;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Downloads ontologies necessary for tests.
 */
public class OntologyDownloader implements LauncherSessionListener {

    private static final Logger LOG = LoggerFactory.getLogger(OntologyDownloader.class);

    private static final Map<String, String> TO_DOWNLOAD = Map.of(
            "https://onto.fel.cvut.cz/ontologies/data-description", "data-description.ttl"
    );

    private static Path directory;

    public static Path getDirectory() {
        return directory;
    }

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        try {
            LOG.debug("Downloading test ontologies...");
            directory = Files.createTempDirectory("termit-ontologies");
            TO_DOWNLOAD.forEach((url, file) -> {
                try {
                    LOG.trace("Downloading ontology <{}>.", url);
                    final HttpURLConnection connection = (HttpURLConnection) new java.net.URL(url).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Accept", "text/turtle");
                    if (connection.getResponseCode() == java.net.HttpURLConnection.HTTP_OK) {
                        var filePath = directory.resolve(file);
                        Files.copy(connection.getInputStream(), filePath);
                        LOG.info("Downloaded ontology '{}' to '{}'.", url, filePath);
                    } else {
                        throw new RuntimeException(
                                "Failed to download ontology " + url + ", got status: " + connection.getResponseCode());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary directory for ontologies.", e);
        }
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        LOG.debug("Cleaning up test ontologies...");
        try {
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
                dirStream.forEach(f -> {
                    try {
                        Files.delete(f);
                    } catch (IOException e) {
                        LOG.error("Failed to delete temporary ontology file {}", f, e);
                    }
                });
                Files.delete(directory);
            }
        } catch (IOException e) {
            LOG.error("Failed to delete temporary ontology directory {}", directory, e);
        }
    }
}
