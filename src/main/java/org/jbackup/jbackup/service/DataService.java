package org.jbackup.jbackup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jbackup.jbackup.data.JBackupData;
import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.properties.JBackupProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

public class DataService {

    private final JBackupProperties properties;

    private JBackupData jBackupData;

    public DataService(JBackupProperties properties) {
        this.properties = Objects.requireNonNull(properties);
        jBackupData = new JBackupData();
    }

    public JBackupData getjBackupData() {
        return jBackupData;
    }

    public void save() {
        try {
            Path p = properties.getGithub().getDataRep();
            if (Files.notExists(p.getParent())) {
                Files.createDirectories(p.getParent());
            }
            if (jBackupData.getDateModification() == null) {
                jBackupData.setDateModification(LocalDateTime.now());
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            mapper.writeValue(p.toFile(), jBackupData);
        } catch (IOException e) {
            throw new JBackupException("Erreur pour sauver le fichier", e);
        }
    }

    public void load() {
        try {
            Path p = properties.getGithub().getDataRep();
            if (Files.exists(p)) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                jBackupData = mapper.readValue(p.toFile(), JBackupData.class);
                if (jBackupData.getDateCreation() == null) {
                    jBackupData.setDateCreation(LocalDateTime.now());
                }
                if (jBackupData.getDateModification() == null) {
                    jBackupData.setDateModification(LocalDateTime.now());
                }
            }
        } catch (IOException e) {
            throw new JBackupException("Erreur pour lire le fichier", e);
        }
    }
}
