package org.jbackup.jbackup.data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class JBackupData {

    private LocalDateTime dateCreation;
    private LocalDateTime dateModification;
    private Map<String, GithubData> listeGithub=new HashMap<>();

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public LocalDateTime getDateModification() {
        return dateModification;
    }

    public void setDateModification(LocalDateTime dateModification) {
        this.dateModification = dateModification;
    }

    public Map<String, GithubData> getListeGithub() {
        return listeGithub;
    }

    public void setListeGithub(Map<String, GithubData> listeGithub) {
        this.listeGithub = listeGithub;
    }
}
