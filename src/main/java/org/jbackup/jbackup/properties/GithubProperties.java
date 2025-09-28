package org.jbackup.jbackup.properties;

import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Period;
import java.util.List;

public class GithubProperties {

    private String user;
    private String dest;
    private String githubUrl;
    private List<String> repos;
    private String token;
    private String apiVersion;
    private boolean mirror;
    private int pageSize;
    private DataSize maxMemory;
    private boolean disabled;
    private Duration connexionTimeout;
    private Duration readTimeout;
    private boolean updateReposByDate;
    private Path dataRep;
    private int nettoyageConcervationMin;
    private Period nettoyagePeriode;

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

    public boolean isMirror() {
        return mirror;
    }

    public void setMirror(boolean mirror) {
        this.mirror = mirror;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public DataSize getMaxMemory() {
        return maxMemory;
    }

    public void setMaxMemory(DataSize maxMemory) {
        this.maxMemory = maxMemory;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public Duration getConnexionTimeout() {
        return connexionTimeout;
    }

    public void setConnexionTimeout(Duration connexionTimeout) {
        this.connexionTimeout = connexionTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public boolean isUpdateReposByDate() {
        return updateReposByDate;
    }

    public void setUpdateReposByDate(boolean updateReposByDate) {
        this.updateReposByDate = updateReposByDate;
    }

    public Path getDataRep() {
        return dataRep;
    }

    public void setDataRep(Path dataRep) {
        this.dataRep = dataRep;
    }

    public int getNettoyageConcervationMin() {
        return nettoyageConcervationMin;
    }

    public void setNettoyageConcervationMin(int nettoyageConcervationMin) {
        this.nettoyageConcervationMin = nettoyageConcervationMin;
    }

    public Period getNettoyagePeriode() {
        return nettoyagePeriode;
    }

    public void setNettoyagePeriode(Period nettoyagePeriode) {
        this.nettoyagePeriode = nettoyagePeriode;
    }
}
