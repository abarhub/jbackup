package org.jbackup.jbackup.data;

import java.time.LocalDateTime;

public class GithubData {

    private String nom;
    private LocalDateTime date;

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }
}
