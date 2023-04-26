package org.jbackup.jbackup.config;

public class GlobalConfig {

    private String compress;

    private boolean verbose;

    public String getCompress() {
        return compress;
    }

    public void setCompress(String compress) {
        this.compress = compress;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
