package org.jbackup.jbackup.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.lang3.StringUtils;
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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class BackupGithubService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupGithubService.class);

    public void backup(GithubProperties github) {
        var debut = Instant.now();
        final int size = 16 * 1024 * 1024;
        final ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(size))
                .build();
        var exchanged = WebClient.builder()
                .baseUrl(github.getGithubUrl())
                .exchangeStrategies(strategies);
        if (StringUtils.isNotBlank(github.getToken())) {
            LOGGER.atInfo().log("api github with token");
            exchanged = exchanged.defaultHeader("Authorization", "Bearer " + github.getToken());
        }
        if (StringUtils.isNotBlank(github.getApiVersion())) {
            LOGGER.atInfo().log("api github version: {}", github.getApiVersion());
            exchanged = exchanged.defaultHeader("X-GitHub-Api-Version", github.getApiVersion());
        }
        WebClient client = exchanged.build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builder(WebClientAdapter.forClient(client)).build();

        var githubService = factory.createClient(GithubService.class);

        final Instant instant = Instant.now();
        LOGGER.atInfo().log("date: {} ({}s)", instant, instant.getEpochSecond());

        enregistreUser(githubService, github, instant);
        enregistreEtoiles(githubService, github, instant);

        enregistreProjets(github, githubService);

        enregistreGist(githubService, github, instant);

        LOGGER.atInfo().log("duree du backup: {}", Duration.between(debut, Instant.now()));
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
                        } catch (JsonProcessingException e) {
                            throw new JBackupException("error for get json", e);
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
        LOGGER.atInfo().log("liste des projet: {}", listeProjet.stream().map(x -> x.getNom()).sorted().toList());
        LOGGER.atInfo().log("liste des projet2: {}", listeProjet.stream().map(x -> x.getNom()).sorted().collect(Collectors.toSet()));

        var pathRoot = Path.of(github.getDest(), "repos");

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
                Path rep = pathRoot.resolve(projet.getNom());
                if (Files.notExists(rep.getParent())) {
                    try {
                        Files.createDirectories(rep.getParent());
                    } catch (IOException e) {
                        throw new JBackupException("Erreur pour créer le répertoire " + rep.getParent(), e);
                    }
                }
                var res = updateGit(rep, projet.getNom(), projet.getCloneUrl());
                if (!res) {
                    LOGGER.atError().log("Erreur pour mettre à jour le reporitory {}", rep);
                    throw new JBackupException("Erreur pour mettre à jour le reporitory " + rep);
                }
            }
        }

        listeProjetsDifferents(listeProjet, pathRoot);

        LOGGER.atInfo().log("Enregistrement des projets OK");
    }

    private static void listeProjetsDifferents(List<Project> listeProjet, Path pathRoot) {
        try {
            var listeRemote = listeProjet.stream().map(x -> x.getNom()).toList();
            var listeLocal = Files.list(pathRoot).map(x -> x.getFileName().toString()).toList();
            LOGGER.atInfo().log("liste des projets en remote : {}", listeRemote);
            LOGGER.atInfo().log("liste des projets en local : {}", listeLocal);
            Set<String> liste1 = new TreeSet<>(listeRemote);
            liste1.removeAll(listeLocal);
            Set<String> liste2 = new TreeSet<>(listeLocal);
            liste2.removeAll(listeRemote);
            LOGGER.atInfo().log("liste des projets en remote en trop: {}", liste1);
            LOGGER.atInfo().log("liste des projet en local en trop: {}", liste2);
        } catch (IOException e) {
            LOGGER.atError().log("Erreur pour liste les projets", e);
        }
    }

    private static boolean updateGit(Path rep, String nom, String cloneUrl) {
        if (Files.exists(rep)) {
            try {
                LOGGER.atInfo().log("pull {}", nom);
                RunProgram runProgram = new RunProgram();
                String[] tab = new String[]{"git", "-C", rep.toString(), "pull", "--all"};
                var res = runProgram.runCommand(true, tab);
                if (res != 0) {
                    LOGGER.error("Erreur res={}", res);
                    return false;
                } else {
                    LOGGER.info("res={}", res);
                    return true;
                }
            } catch (IOException | InterruptedException e) {
                throw new JBackupException("Erreur pour executer le pull vers " + rep, e);
            }
        } else {
            try {
                LOGGER.atInfo().log("clone {} ({})", nom, cloneUrl);
                RunProgram runProgram = new RunProgram();
                String[] tab = new String[]{"git", "clone", cloneUrl, rep.toString()};
                var res = runProgram.runCommand(true, tab);
                if (res != 0) {
                    LOGGER.error("Erreur res={}", res);
                    return false;
                } else {
                    LOGGER.info("res={}", res);
                    return true;
                }
            } catch (IOException | InterruptedException e) {
                throw new JBackupException("Erreur pour executer le clone vers " + rep, e);
            }
        }
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
                        LOGGER.atDebug().log("response:  {}", body);

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

    private void enregistreGist(GithubService githubService, GithubProperties github, Instant instant) {
        var fin = false;
        var no = 0;
        LOGGER.atInfo().log("Enregistrement gist ...");
        do {
            Map<String, Object> map = new HashMap<>();
            map.put("per_page", 100);
            map.put("page", no);
            LOGGER.atInfo().log("call github starred (page: {}) ...", no);
            var responseEntityMono = githubService.getGist(github.getUser(), map);
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

                        var rep = Paths.get(github.getDest(), "gist");
                        try {
                            var rep3 = rep.resolve("gist_meta");
                            Files.createDirectories(rep3);
                            var path2 = rep3.resolve("gist_meta_" + instant.getEpochSecond() + "_" + no + ".json");
                            ObjectMapper mapper = new ObjectMapper()
                                    .enable(SerializationFeature.INDENT_OUTPUT);
                            JsonNode jsonNode = mapper.readTree(body);

                            if (jsonNode != null && jsonNode.isArray()) {
                                if (!jsonNode.isEmpty()) {
                                    aucuneDonnees = false;

                                    LOGGER.atInfo().log("Ecriture gist dans le fichier: {}", path2);
                                    try (var f = Files.newBufferedWriter(path2, StandardCharsets.UTF_8)) {
                                        mapper.writeValue(f, jsonNode);
                                    }

                                    for (int i = 0; i < jsonNode.size(); i++) {
                                        var node = jsonNode.get(i);
                                        var id = node.get("id").asText();
                                        var urlPull = node.get("git_pull_url").asText();
                                        var description = node.get("description").asText();
                                        if (StringUtils.isNotBlank(id)) {
                                            if (StringUtils.isNotBlank(urlPull)) {
                                                var rep2 = rep.resolve(id).resolve("repo").resolve(id + ".git");
                                                if (Files.notExists(rep2.getParent())) {
                                                    Files.createDirectories(rep2.getParent());
                                                }
                                                LOGGER.atInfo().log("mise à jour de {}", rep2);
                                                var res = updateGit(rep2, id, urlPull);
                                                if (!res) {
                                                    LOGGER.atError().log("Erreur pour mettre à jour le reporitory {}", rep2);
                                                    throw new JBackupException("Erreur pour mettre à jour le reporitory " + rep);
                                                }
                                            }
                                            if (StringUtils.isNotBlank(description)) {
                                                var rep2 = rep.resolve(id).resolve("description.txt");
                                                var copie = true;
                                                if (Files.exists(rep2)) {
                                                    var s = Files.readString(rep2, StandardCharsets.UTF_8);
                                                    if (s != null && Objects.equals(description, s)) {
                                                        copie = false;
                                                    }
                                                }
                                                if (copie) {
                                                    LOGGER.atInfo().log("création du fichier {}", rep2);
                                                    Files.writeString(rep2, description, StandardCharsets.UTF_8);
                                                }
                                            }
                                        }
                                    }

                                }
                            }
                        } catch (IOException e) {
                            throw new JBackupException("Erreur pour enregistre le gist", e);
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
        LOGGER.atInfo().log("Enregistrement gist ok");
    }
}
