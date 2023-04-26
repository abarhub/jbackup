package org.jbackup.jbackup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties("jbackup")
public class JBackupConfig {

    private GlobalConfig global;
    private Map<String, SaveConfig> dir;

    public GlobalConfig getGlobal() {
        return global;
    }

    public void setGlobal(GlobalConfig global) {
        this.global = global;
    }

    public Map<String, SaveConfig> getDir() {
        return dir;
    }

    public void setDir(Map<String, SaveConfig> dir) {
        this.dir = dir;
    }
}
