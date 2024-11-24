package org.jbackup.jbackup;


import org.jbackup.jbackup.properties.PocketProperties;
import org.jbackup.jbackup.service.PocketService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Test1 {

    private static final Logger LOGGER = LoggerFactory.getLogger(PocketService.class);

    @Test
    void test1() {
        try {
            appel();
        } catch (Exception e) {
            LOGGER.error("Erreur dans le teste", e);
            throw e;
        }
    }

    private void appel() {
        try {
            LOGGER.info("Starting test1");

            var consumerKey = System.getProperty("consumerKey");
            var redirectUrl = System.getProperty("urlRedirect");

            LOGGER.info("Consumer key: {}", consumerKey);
            LOGGER.info("Redirect url: {}", redirectUrl);

            PocketProperties pocketProperties = new PocketProperties();
            pocketProperties.setBaseUrl("https://getpocket.com");
            pocketProperties.setoAuthUrl("/v3/oauth/request");
            pocketProperties.setGetUrl("/v3/get");
            pocketProperties.setConsumerKey(consumerKey);
            pocketProperties.setRedirectUrl(redirectUrl);

            var pocketService = new PocketService(pocketProperties);
            pocketService.appel();
            LOGGER.info("Ending test1");
        } catch (Exception e) {
            LOGGER.error("Erreur", e);
            throw e;
        }
    }
}
