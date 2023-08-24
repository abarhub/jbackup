package org.jbackup.jbackup.properties;

import java.util.List;

public class GithubProperties {

    private String user;
    private String dest;
    private String githubUrl;
    private List<String> repos;
    private String token;
    private String apiVersion;

    private boolean disabled;

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getDest() {
        return dest;
    }

    public void setDest(String dest) {
        this.dest = dest;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public List<String> getRepos() {
        return repos;
    }

    public void setRepos(List<String> repos) {
        this.repos = repos;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
}
