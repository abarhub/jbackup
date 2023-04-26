package org.jbackup.jbackup.config;

import org.springframework.util.unit.DataSize;

public class GlobalConfig {

    private String compress;

    private boolean verbose;

    private boolean crypt;

    private String password;

    private DataSize splitSize;

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

    public boolean isCrypt() {
        return crypt;
    }

    public void setCrypt(boolean crypt) {
        this.crypt = crypt;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public DataSize getSplitSize() {
        return splitSize;
    }

    public void setSplitSize(DataSize splitSize) {
        this.splitSize = splitSize;
    }
}
