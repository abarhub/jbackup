package org.jbackup.jbackup.service;


import org.jbackup.jbackup.data.JBackupData;
import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.properties.JBackupProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
                jBackupData = mapper.readValue(p.toFile(), JBackupData.class);
                if (jBackupData.getDateCreation() == null) {
                    jBackupData.setDateCreation(LocalDateTime.now());
                }
                if (jBackupData.getDateModification() == null) {
                    jBackupData.setDateModification(LocalDateTime.now());
                }
            }
        } catch (JacksonException e) {
            throw new JBackupException("Erreur pour lire le fichier", e);
        }
    }
}
