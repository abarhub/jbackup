package org.jbackup.jbackup.service;

import org.apache.commons.lang3.StringUtils;
import org.jbackup.jbackup.pocket.PocketGetRequest;
import org.jbackup.jbackup.pocket.PocketOAuthRequest;
import org.jbackup.jbackup.pocket.PocketOAuthResponse;
import org.jbackup.jbackup.properties.PocketProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public class PocketService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PocketService.class);

    private final PocketProperties pocketProperties;

    public PocketService(PocketProperties pocketProperties) {
        this.pocketProperties = pocketProperties;
    }

    public void appel() {

        var urlAuth = this.pocketProperties.getBaseUrl() + this.pocketProperties.getoAuthUrl();
        var consumerKey = this.pocketProperties.getConsumerKey();
        var url = this.pocketProperties.getRedirectUrl();
        var urlGet = this.pocketProperties.getBaseUrl() + this.pocketProperties.getGetUrl();

        PocketOAuthRequest request = new PocketOAuthRequest(consumerKey, url);

        RestClient restClient = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory())
//                .baseUrl(urlAuth)
//                .defaultHeader("Content-Type", "application/json; charset=UTF-8")
//                .defaultHeader("X-Accept", "application/json")
                .build();
        var response = restClient.post()
                .uri(urlAuth)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Accept", "application/json")
                .body(request)
                .retrieve()
                .toEntity(PocketOAuthResponse.class);

        LOGGER.atInfo().log("code http: {}, has body: {}", response.getStatusCode(), response.hasBody());
        var body = response.getBody();
        LOGGER.atInfo().log("body: {}", body);

        if (body != null && StringUtils.isNotBlank(body.code())) {

            LOGGER.atInfo().log("appel get ...");

            var request2 = new PocketGetRequest(consumerKey, body.code(), "10", "simple");

            var response2 = restClient.post()
                    .uri(urlGet)
                    .header("Content-Type", "application/json")
                    .header("X-Accept", "application/json")
                    .body(request2)
                    .retrieve()
                    .toEntity(String.class);

            LOGGER.atInfo().log("code http: {}, has body: {}", response2.getStatusCode(), response2.hasBody());
            var body2 = response2.getBody();
            LOGGER.atInfo().log("body: {}", body2);
        }
    }
}
