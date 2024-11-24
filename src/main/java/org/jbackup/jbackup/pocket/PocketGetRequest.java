package org.jbackup.jbackup.pocket;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PocketGetRequest(@JsonProperty("consumer_key") String consumerKey,
                               @JsonProperty("access_token") String accessKey,
                               String count,
                               String detailType) {
}
