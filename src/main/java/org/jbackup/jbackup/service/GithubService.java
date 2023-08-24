package org.jbackup.jbackup.service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface GithubService {

    @GetExchange("/{user}/repos")
    Mono<ResponseEntity<String>> getRepos(@PathVariable String user, @RequestParam Map<String,Object> parametres);

    @GetExchange("/{user}")
    Mono<ResponseEntity<String>> getUser(@PathVariable String user);

    @GetExchange("/{user}/starred")
    Mono<ResponseEntity<String>> getStarred(@PathVariable String user, @RequestParam Map<String,Object> parametres);

}
