package org.jbackup.jbackup.domain;

import java.util.StringJoiner;

public class Project {

    private String nom;
    private String url;
    private String cloneUrl;

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

    @Override
    public String toString() {
        return new StringJoiner(", ", Project.class.getSimpleName() + "[", "]")
                .add("nom='" + nom + "'")
                .add("url='" + url + "'")
                .add("cloneUrl='" + cloneUrl + "'")
                .toString();
    }
}
