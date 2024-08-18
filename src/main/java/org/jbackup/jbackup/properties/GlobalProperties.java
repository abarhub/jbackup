package org.jbackup.jbackup.properties;

import org.jbackup.jbackup.config.CompressType;
import org.springframework.util.unit.DataSize;

import java.time.LocalDateTime;

public class GlobalProperties {

    private CompressType compress;

    private boolean verbose;

    private boolean crypt;

    private String password;

    private DataSize splitSize;

    private String path7zip;

    private boolean shadowCopy;

    private LocalDateTime dateLimite;

    private int cryptageBuffer;

    private boolean desactiveCryptage;

    private boolean desactiveHash;

    private boolean desactiveVerificationCryptage;

    private boolean desactiveVerificationZip;

    private boolean desactiveVerificationHash;

    private int zipBuffer;


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

    public boolean isShadowCopy() {
        return shadowCopy;
    }

    public void setShadowCopy(boolean shadowCopy) {
        this.shadowCopy = shadowCopy;
    }

    public LocalDateTime getDateLimite() {
        return dateLimite;
    }

    public void setDateLimite(LocalDateTime dateLimite) {
        this.dateLimite = dateLimite;
    }

    public int getCryptageBuffer() {
        return cryptageBuffer;
    }

    public void setCryptageBuffer(int cryptageBuffer) {
        this.cryptageBuffer = cryptageBuffer;
    }

    public boolean isDesactiveCryptage() {
        return desactiveCryptage;
    }

    public void setDesactiveCryptage(boolean desactiveCryptage) {
        this.desactiveCryptage = desactiveCryptage;
    }

    public boolean isDesactiveHash() {
        return desactiveHash;
    }

    public void setDesactiveHash(boolean desactiveHash) {
        this.desactiveHash = desactiveHash;
    }

    public boolean isDesactiveVerificationCryptage() {
        return desactiveVerificationCryptage;
    }

    public void setDesactiveVerificationCryptage(boolean desactiveVerificationCryptage) {
        this.desactiveVerificationCryptage = desactiveVerificationCryptage;
    }

    public boolean isDesactiveVerificationZip() {
        return desactiveVerificationZip;
    }

    public void setDesactiveVerificationZip(boolean desactiveVerificationZip) {
        this.desactiveVerificationZip = desactiveVerificationZip;
    }

    public boolean isDesactiveVerificationHash() {
        return desactiveVerificationHash;
    }

    public void setDesactiveVerificationHash(boolean desactiveVerificationHash) {
        this.desactiveVerificationHash = desactiveVerificationHash;
    }

    public int getZipBuffer() {
        return zipBuffer;
    }

    public void setZipBuffer(int zipBuffer) {
        this.zipBuffer = zipBuffer;
    }
}
