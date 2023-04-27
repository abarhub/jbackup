package org.jbackup.jbackup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties("jbackup")
public class JBackupProperties {

    private GlobalProperties global;
    private Map<String, SaveProperties> dir;

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
}
