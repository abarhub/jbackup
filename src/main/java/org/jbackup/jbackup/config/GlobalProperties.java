package org.jbackup.jbackup.config;

import org.springframework.util.unit.DataSize;

public class GlobalProperties {

    private CompressType compress;

    private boolean verbose;

    private boolean crypt;

    private String password;

    private DataSize splitSize;

    private String path7zip;

    public CompressType getCompress() {
        return compress;
    }

    public void setCompress(CompressType compress) {
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

    public String getPath7zip() {
        return path7zip;
    }

    public void setPath7zip(String path7zip) {
        this.path7zip = path7zip;
    }
}
