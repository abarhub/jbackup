package org.jbackup.jbackup.service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface GithubService {

    @GetExchange("/users/{user}/repos")
    Mono<ResponseEntity<String>> getRepos(@PathVariable String user, @RequestParam Map<String,Object> parametres);

    @GetExchange("/users/{user}")
    Mono<ResponseEntity<String>> getUser(@PathVariable String user);

    @GetExchange("/users/{user}/starred")
    Mono<ResponseEntity<String>> getStarred(@PathVariable String user, @RequestParam Map<String,Object> parametres);

    @GetExchange("/users/{user}/gists")
    Mono<ResponseEntity<String>> getGist(@PathVariable String user, @RequestParam Map<String,Object> parametres);

    @GetExchange("/repos/{user}/{project}/releases")
    Mono<ResponseEntity<String>> getRelease(@PathVariable String user, @PathVariable String project,
                                         @RequestParam Map<String,Object> parametres);



}
