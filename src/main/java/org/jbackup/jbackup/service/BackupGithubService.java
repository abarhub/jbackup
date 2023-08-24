package org.jbackup.jbackup.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jbackup.jbackup.domain.Project;
import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.properties.GithubProperties;
import org.jbackup.jbackup.utils.RunProgram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BackupGithubService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupGithubService.class);

    public void backup(GithubProperties github) {
//        RestClient restClient = RestClient.create();
        final int size = 16 * 1024 * 1024;
        final ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(size))
                .build();
        WebClient client = WebClient.builder()
                .baseUrl(github.getGithubUrl())
                .exchangeStrategies(strategies)
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builder(WebClientAdapter.forClient(client)).build();

        var githubService = factory.createClient(GithubService.class);

        final Instant instant = Instant.now();
        LOGGER.atInfo().log("date: {} ({}s)", instant, instant.getEpochSecond());

        //enregistreUser(githubService, github, instant);
        //enregistreEtoiles(githubService, github, instant);

        enregistreProjets(github, githubService);
    }

    private static void enregistreProjets(GithubProperties github, GithubService githubService) {
        var fin = false;
        var no = 1;
        List<Project> listeProjet = new ArrayList<>();
        LOGGER.atInfo().log("Enregistrement des projets ...");
        do {
            Map<String, Object> map = new HashMap<>();
            map.put("per_page", 100);
            map.put("page", no);
            LOGGER.atInfo().log("call github (page: {}) ...", no);
            var responseEntityMono = githubService.getRepos(github.getUser(), map);
            var responseOpt = responseEntityMono
                    //.log()
                    .doOnError(x -> LOGGER.atError().log("error:  {}", x.getMessage()))
                    .blockOptional();
            if (responseOpt.isPresent()) {
                var response = responseOpt.get();
                LOGGER.atInfo().log("response:  {}", response.getStatusCode());
                boolean aucuneDonnees = true;
                if (response.getStatusCode().is2xxSuccessful()) {
                    if (response.hasBody()) {
                        String body = response.getBody();
                        LOGGER.atDebug().log("response:  {}", body);
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode jsonNode = mapper.readTree(body);
                            if (jsonNode != null && jsonNode.isArray()) {
                                if (jsonNode.size() > 0) {
                                    aucuneDonnees = false;
                                }
                                for (int i = 0; i < jsonNode.size(); i++) {
                                    var project = jsonNode.get(i);
                                    var nom = project.get("name").asText();
                                    var url = project.get("url").asText();
                                    var urlClone = project.get("clone_url").asText();
                                    var projet = new Project();
                                    projet.setNom(nom);
                                    projet.setUrl(url);
                                    projet.setCloneUrl(urlClone);
                                    listeProjet.add(projet);
                                    LOGGER.atInfo().log("{}: {} ({})", nom, url, urlClone);
                                }
                            }
                        } catch (JsonMappingException e) {
                            throw new RuntimeException(e);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    if (aucuneDonnees) {
                        fin = true;
                    }
                } else {
                    fin = true;
                }
            } else {
                fin = true;
            }
            no++;
        } while (!fin);

        LOGGER.atInfo().log("Nombre de projet: {}", listeProjet.size());
        LOGGER.atInfo().log("liste des projet: {}", listeProjet.stream().map(x->x.getNom()).sorted().toList());
        LOGGER.atInfo().log("liste des projet2: {}", listeProjet.stream().map(x->x.getNom()).sorted().collect(Collectors.toSet()));

        for (var projet : listeProjet) {
            var aTraiter = false;
            if (CollectionUtils.isEmpty(github.getRepos())) {
                aTraiter = true;
            } else if (github.getRepos().contains(projet.getNom())) {
                aTraiter = true;
            } else {
                LOGGER.atInfo().log("Projet ignore: {}", projet.getNom());
            }
            if (aTraiter) {
                LOGGER.info("traitement de {}", projet.getNom());
                Path rep = Path.of(github.getDest(), projet.getNom());
                if (Files.exists(rep)) {
                    try {
                        LOGGER.atInfo().log("pull {}",projet.getNom());
                        RunProgram runProgram = new RunProgram();
                        String[] tab = new String[]{"git", "-C", rep.toString(), "pull", "--all"};
                        var res = runProgram.runCommand(tab);
                        LOGGER.info("res={}", res);
                    } catch (IOException | InterruptedException e) {
                        throw new JBackupException("Erreur pour executer le clone vers " + rep, e);
                    }
                } else {
                    try {
                        LOGGER.atInfo().log("clone {} ({})",projet.getNom(), projet.getCloneUrl());
                        RunProgram runProgram = new RunProgram();
                        String[] tab = new String[]{"git", "clone", projet.getCloneUrl(), rep.toString()};
                        var res = runProgram.runCommand(tab);
                        LOGGER.info("res={}", res);
                    } catch (IOException | InterruptedException e) {
                        throw new JBackupException("Erreur pour executer le clone vers " + rep, e);
                    }
                }

            }
        }
        LOGGER.atInfo().log("Enregistrement des projets OK");
    }

    private void enregistreUser(GithubService githubService, GithubProperties github, Instant instant) {
        LOGGER.atInfo().log("Enregistrement de l'utilisateur ...");
        var responseEntityMono = githubService.getUser(github.getUser());
        var responseEntityMonoOptional = responseEntityMono.blockOptional();
        if (responseEntityMonoOptional.isPresent()) {
            var responseEntity = responseEntityMonoOptional.get();
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.hasBody()) {
                var res = responseEntity.getBody();
                var rep = Paths.get(github.getDest(), "github_user");
                try {
                    var path2 = rep.resolve("github_user_" + instant.getEpochSecond() + ".json");
                    ObjectMapper mapper = new ObjectMapper()
                            .enable(SerializationFeature.INDENT_OUTPUT);
                    JsonNode jsonNode = mapper.readTree(res);
                    Files.createDirectories(rep);
                    LOGGER.atInfo().log("Ecriture des info de l'utilisateur dans le fichier: {}", path2);
                    try (var f = Files.newBufferedWriter(path2, StandardCharsets.UTF_8)) {
                        mapper.writeValue(f, jsonNode);
                    }
                } catch (IOException e) {
                    throw new JBackupException("Erreur pour enregistre les informations de l'utilisateur", e);
                }
            }
        }
        LOGGER.atInfo().log("Enregistrement de l'utilisateur ok");
    }

    private void enregistreEtoiles(GithubService githubService, GithubProperties github, Instant instant) {
        var fin = false;
        var no = 0;
        LOGGER.atInfo().log("Enregistrement des étoiles ...");
        do {
            Map<String, Object> map = new HashMap<>();
            map.put("per_page", 100);
            map.put("page", no);
            LOGGER.atInfo().log("call github starred (page: {}) ...", no);
            var responseEntityMono = githubService.getStarred(github.getUser(), map);
            var responseOpt = responseEntityMono
                    //.log()
                    .doOnError(x -> LOGGER.atError().log("error:  {}", x.getMessage()))
                    .blockOptional();
            if (responseOpt.isPresent()) {
                var response = responseOpt.get();
                LOGGER.atInfo().log("response:  {}", response.getStatusCode());
                boolean aucuneDonnees = true;
                if (response.getStatusCode().is2xxSuccessful()) {
                    if (response.hasBody()) {
                        String body = response.getBody();
                        LOGGER.atInfo().log("response:  {}", body);

                        var rep = Paths.get(github.getDest(), "starred");
                        try {
                            Files.createDirectories(rep);
                            var path2 = rep.resolve("starred_" + instant.getEpochSecond() + "_" + no + ".json");
                            ObjectMapper mapper = new ObjectMapper()
                                    .enable(SerializationFeature.INDENT_OUTPUT);
                            JsonNode jsonNode = mapper.readTree(body);

                            if (jsonNode != null && jsonNode.isArray()) {
                                if (!jsonNode.isEmpty()) {
                                    aucuneDonnees = false;

                                    LOGGER.atInfo().log("Ecriture des info de l'utilisateur dans le fichier: {}", path2);
                                    try (var f = Files.newBufferedWriter(path2, StandardCharsets.UTF_8)) {
                                        mapper.writeValue(f, jsonNode);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            throw new JBackupException("Erreur pour enregistre les informations de l'utilisateur", e);
                        }
                    }
                    if (aucuneDonnees) {
                        fin = true;
                    }
                } else {
                    fin = true;
                }
            } else {
                fin = true;
            }
            no++;
        } while (!fin);
        LOGGER.atInfo().log("Enregistrement des étoiles ok");
    }
}
