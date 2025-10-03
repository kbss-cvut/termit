package cz.cvut.kbss.termit.util;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;

public class FileUtils {
    private static final Logger LOG = LoggerFactory.getLogger(FileUtils.class);
    private FileUtils() {
        throw new AssertionError();
    }
    /**
     * Resolves {@code file} inside the {@code termitDir}.
     * If instructed to verify the file existence, throws then the resolved path is not a file (or does not exist).
     * @param termitDir the directory with termit files
     * @param file the file to resol ve
     * @param verifyExists whether check for file existence
     * @return the resolved file in the file system
     */
    public static java.io.File resolveTermitFile(Path termitDir, File file, boolean verifyExists) {
        Objects.requireNonNull(file);
        final Path path = termitDir.resolve(file.getDirectoryName())
                                             .resolve(IdentifierResolver.sanitizeFileName(file.getLabel()));
        final java.io.File result = path.toFile();
        if (verifyExists && !result.isFile()) {
            LOG.error("File {} not found at location {}.", file, path);
            throw new NotFoundException("File " + file + " not found on file system.");
        }
        return result;
    }
}
