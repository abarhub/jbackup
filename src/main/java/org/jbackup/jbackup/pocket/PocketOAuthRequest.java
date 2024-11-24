package org.jbackup.jbackup.pocket;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PocketOAuthRequest(@JsonProperty("consumer_key") String consumerKey,
                                 @JsonProperty("redirect_uri") String url) {
}
