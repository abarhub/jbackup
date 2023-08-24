package org.jbackup.jbackup.properties;

import org.jbackup.jbackup.config.SaveProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties("jbackup")
public class JBackupProperties {

    private GlobalProperties global;
    private Map<String, SaveProperties> dir;

    private GithubProperties github;

    public GlobalProperties getGlobal() {
        return global;
    }

    public void setGlobal(GlobalProperties global) {
        this.global = global;
    }

    public Map<String, SaveProperties> getDir() {
        return dir;
    }

    public void setDir(Map<String, SaveProperties> dir) {
        this.dir = dir;
    }

    public GithubProperties getGithub() {
        return github;
    }

    public void setGithub(GithubProperties github) {
        this.github = github;
    }
}
