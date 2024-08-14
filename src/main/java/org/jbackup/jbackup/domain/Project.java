package org.jbackup.jbackup.domain;

import java.time.LocalDateTime;
import java.util.StringJoiner;

public class Project {

    private String nom;
    private String url;
    private String cloneUrl;
    private boolean hasWiki;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime pushedAt;

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCloneUrl() {
        return cloneUrl;
    }

    public void setCloneUrl(String cloneUrl) {
        this.cloneUrl = cloneUrl;
    }

    public boolean isHasWiki() {
        return hasWiki;
    }

    public void setHasWiki(boolean hasWiki) {
        this.hasWiki = hasWiki;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getPushedAt() {
        return pushedAt;
    }

    public void setPushedAt(LocalDateTime pushedAt) {
        this.pushedAt = pushedAt;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Project.class.getSimpleName() + "[", "]")
                .add("nom='" + nom + "'")
                .add("url='" + url + "'")
                .add("cloneUrl='" + cloneUrl + "'")
                .add("hasWiki=" + hasWiki)
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .add("pushedAt=" + pushedAt)
                .toString();
    }
}
