package org.jbackup.jbackup.properties;

public class PocketProperties {

    private String baseUrl;
    private String oAuthUrl;
    private String getUrl;
    private String consumerKey;
    private String redirectUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getoAuthUrl() {
        return oAuthUrl;
    }

    public void setoAuthUrl(String oAuthUrl) {
        this.oAuthUrl = oAuthUrl;
    }

    public String getGetUrl() {
        return getUrl;
    }

    public void setGetUrl(String getUrl) {
        this.getUrl = getUrl;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }
}
