package cz.cvut.kbss.termit.rest.dto;

import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.termit.service.document.backup.BackupFile;
import cz.cvut.kbss.termit.service.document.backup.BackupReason;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.time.Instant;
import java.util.Objects;

/**
 * Dto used for description of a file backup created at {@link #timestamp}
 * with a {@link #backupReason}
 */
@NonEntity
@OWLClass(iri = Vocabulary.s_c_popis_zalohy_souboru)
public class FileBackupDto {
    @OWLAnnotationProperty(iri = Vocabulary.s_p_ma_datum_a_cas_vytvoreni)
    private Instant timestamp;
    @OWLDataProperty(iri = Vocabulary.s_p_ma_duvod_zalohy)
    private BackupReason backupReason;

    /**
     * @param timestamp    the timestamp at which the backup was created
     * @param backupReason the reason of backup creation
     */
    public FileBackupDto(Instant timestamp, BackupReason backupReason) {
        this.timestamp = timestamp;
        this.backupReason = backupReason;
    }

    protected FileBackupDto() {
        // default constructor and setters for deserialization
    }

    public FileBackupDto(BackupFile backupFile) {
        this(backupFile.timestamp(), backupFile.backupReason());
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public BackupReason getBackupReason() {
        return backupReason;
    }

    protected void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    protected void setBackupReason(BackupReason backupReason) {
        this.backupReason = backupReason;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (FileBackupDto) obj;
        return Objects.equals(this.timestamp, that.timestamp) &&
                Objects.equals(this.backupReason, that.backupReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, backupReason);
    }

    @Override
    public String toString() {
        return "FileBackupDto[" +
                "timestamp=" + timestamp + ", " +
                "backupReason=" + backupReason + ']';
    }
}
