package org.jbackup.jbackup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.FileUtils;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class BackupGithubService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupGithubService.class);

    private static final ObjectMapper MAPPER_WRITER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private int nbGetRepos;
    private int nbGetUser;
    private int nbGetStarred;
    private int nbGetGist;
    private int nbGetRelease;
    private int nbGitRepo;
    private int nbGitGist;

    public void backup(GithubProperties github) {
        var debut = Instant.now();
        final var githubService = getGithubService(github);

        final Instant instant = Instant.now();
        LOGGER.atInfo().log("date: {} ({}s)", instant, instant.getEpochSecond());

//        enregistreUser(githubService, github, instant);
//        enregistreEtoiles(githubService, github, instant);

        enregistreProjets(github, githubService, instant);

//        enregistreGist(githubService, github, instant);

        LOGGER.atInfo().log("nb appels: {} (user:{}, repos:{}, started:{}, " +
                        "gist: {}, release: {}, git repos: {}, git gist: {})",
                nbGetUser + nbGetRepos + nbGetStarred + nbGetGist + nbGetRelease,
                nbGetUser, nbGetRepos, nbGetStarred, nbGetGist, nbGetRelease,
                nbGitRepo, nbGitGist);

        LOGGER.atInfo().log("duree du backup: {}", Duration.between(debut, Instant.now()));
    }

    private GithubService getGithubService(GithubProperties github) {
        final int size = Math.toIntExact(github.getMaxMemory().toBytes());
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

        return factory.createClient(GithubService.class);
    }

    private void enregistreProjets(GithubProperties github, GithubService githubService, Instant instant) {

        LOGGER.atInfo().log("Enregistrement des projets ...");
        var pathRoot = Path.of(github.getDest(), "repos");
        var listeProjet = getListProjets(github, githubService, instant, pathRoot);

        LOGGER.atInfo().log("Nombre de projets: {}", listeProjet.size());
        LOGGER.atInfo().log("liste des projets: {}", listeProjet.stream().map(Project::getNom).sorted().toList());


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
                var res = updateGit(rep, projet.getNom(), projet.getCloneUrl(), github.isMirror());
                nbGitRepo++;
                if (!res) {
                    LOGGER.atError().log("Erreur pour mettre à jour le reporitory {}", rep);
                    throw new JBackupException("Erreur pour mettre à jour le reporitory " + rep);
                }

                enregistreReleases(githubService, github, instant, projet);
            }
        }

        listeProjetsDifferents(listeProjet, pathRoot);

        LOGGER.atInfo().log("Enregistrement des projets OK");
    }

    private List<Project> getListProjets(GithubProperties github, GithubService githubService, Instant instant, Path pathRoot) {
        boolean fin = false;
        List<Project> listeProjet = new ArrayList<>();
        var no = 1;
        do {
            Map<String, Object> map = new HashMap<>();
            map.put("per_page", 100);
            map.put("page", no);
            LOGGER.atInfo().log("call github (page: {}) ...", no);
            var responseEntityMono = githubService.getRepos(github.getUser(), map);
            nbGetRepos++;
            var responseOpt = responseEntityMono
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
                        var path = pathRoot.resolve("github_metadata")
                                .resolve("github_meta_" + instant.getEpochSecond() + "_" + no + ".json");
                        var jsonNode = enregistreJsonSiNonVide(path, body);
                        if (jsonNode != null) {
                            aucuneDonnees = false;
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

        return listeProjet;
    }

    private void listeProjetsDifferents(List<Project> listeProjet, Path pathRoot) {
        try {
            var listeRemote = listeProjet.stream().map(x -> x.getNom()).toList();
            var listeLocal = Files.list(pathRoot).map(x -> x.getFileName().toString()).toList();
            LOGGER.atDebug().log("liste des projets en remote : {}", listeRemote);
            LOGGER.atDebug().log("liste des projets en local : {}", listeLocal);
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

    private boolean updateGit(Path rep, String nom, String cloneUrl, boolean mirror) {
        String[] tab;
        if (mirror) {

            LOGGER.atInfo().log("clone mirror {} ({})", nom, cloneUrl);
            tab = new String[]{"git", "clone", "--mirror", cloneUrl, rep.toString()};
        } else {
            if (Files.exists(rep)) {
                LOGGER.atInfo().log("pull {}", nom);
                tab = new String[]{"git", "-C", rep.toString(), "pull", "--all"};
            } else {
                LOGGER.atInfo().log("clone {} ({})", nom, cloneUrl);
                tab = new String[]{"git", "clone", cloneUrl, rep.toString()};
            }
        }
        try {
            RunProgram runProgram = new RunProgram();
            var res = runProgram.runCommand(true, tab);
            if (res != 0) {
                LOGGER.error("Erreur res={}", res);
                return false;
            } else {
                LOGGER.info("res={}", res);
                return true;
            }
        } catch (IOException | InterruptedException e) {
            throw new JBackupException("Erreur pour executer la commande git " + Arrays.toString(tab) + " vers " + rep, e);
        }
    }

    private void enregistreUser(GithubService githubService, GithubProperties github, Instant instant) {
        LOGGER.atInfo().log("Enregistrement de l'utilisateur ...");
        var responseEntityMono = githubService.getUser(github.getUser());
        nbGetUser++;
        var responseEntityMonoOptional = responseEntityMono.blockOptional();
        if (responseEntityMonoOptional.isPresent()) {
            var responseEntity = responseEntityMonoOptional.get();
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.hasBody()) {
                var res = responseEntity.getBody();
                var rep = Paths.get(github.getDest(), "github_user");
                var path2 = rep.resolve("github_user_" + instant.getEpochSecond() + ".json");
                enregistreJson(path2, res);
            }
        }
        LOGGER.atInfo().log("Enregistrement de l'utilisateur ok");
    }

    private void enregistreJson(Path file, String contenuJson) {
        try {

            if (Files.notExists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            JsonNode jsonNode = MAPPER_WRITER.readTree(contenuJson);
            LOGGER.atInfo().log("Ecriture dans le fichier: {}", file);
            try (var f = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                MAPPER_WRITER.writeValue(f, jsonNode);
            }
        } catch (IOException e) {
            throw new JBackupException("Erreur pour enregistre le fichier json: " + file, e);
        }
    }

    private JsonNode enregistreJsonSiNonVide(Path file, String contenuJson) {
        try {
            JsonNode jsonNode = MAPPER_WRITER.readTree(contenuJson);
            if (jsonNode != null && jsonNode.isArray() && !jsonNode.isEmpty()) {
                if (Files.notExists(file.getParent())) {
                    Files.createDirectories(file.getParent());
                }
                LOGGER.atInfo().log("Ecriture dans le fichier: {}", file);
                try (var f = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    MAPPER_WRITER.writeValue(f, jsonNode);
                }
                return jsonNode;
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new JBackupException("Erreur pour enregistre le fichier json: " + file, e);
        }
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
            nbGetStarred++;
            var responseOpt = responseEntityMono
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
                            var res = enregistreJsonSiNonVide(path2, body);
                            if (res != null) {
                                aucuneDonnees = false;
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
            nbGetGist++;
            var responseOpt = responseEntityMono
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
                            var jsonNode = enregistreJsonSiNonVide(path2, body);

                            if (jsonNode != null) {
                                aucuneDonnees = false;


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
                                            var res = updateGit(rep2, id, urlPull, github.isMirror());
                                            nbGitGist++;
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

    private void enregistreReleases(GithubService githubService, GithubProperties github, Instant instant, Project projet) {
        var fin = false;
        var no = 0;
        LOGGER.atInfo().log("Enregistrement release ...");
        do {
            Map<String, Object> map = new HashMap<>();
            map.put("per_page", 100);
            map.put("page", no);
            LOGGER.atInfo().log("call github release (page: {}) ...", no);
            var responseEntityMono = githubService.getRelease(github.getUser(), projet.getNom(), map);
            nbGetRelease++;
            var responseOpt = responseEntityMono
                    .onErrorResume(WebClientResponseException.class,
                            ex -> ex.getStatusCode().value() == 404 ? Mono.empty() : Mono.error(ex))
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

                        var rep = Paths.get(github.getDest(), "release");
                        try {
                            var rep3 = rep.resolve(projet.getNom());
                            var path2 = rep3.resolve("release_meta_" + instant.getEpochSecond() + "_" + no + ".json");
                            var jsonNode = enregistreJsonSiNonVide(path2, body);

                            if (jsonNode != null) {
                                aucuneDonnees = false;

                                for (int i = 0; i < jsonNode.size(); i++) {
                                    var node = jsonNode.get(i);
                                    var tagName = node.get("tag_name").asText();
                                    if (StringUtils.isNotBlank(tagName)) {
                                        var path3 = rep3.resolve(tagName);
                                        if (node.has("assets")) {
                                            var assets = node.get("assets");
                                            if (assets != null && assets.isArray() && !assets.isEmpty()) {
                                                if (Files.notExists(path3)) {
                                                    Files.createDirectories(path3);
                                                }
                                                for (int j = 0; j < assets.size(); j++) {
                                                    var asset = assets.get(j);
                                                    var name = asset.get("name").asText();
                                                    var urlFile = asset.get("browser_download_url").asText();
                                                    if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(urlFile)) {
                                                        var f = path3.resolve(name);
                                                        if (Files.notExists(f)) {
                                                            LOGGER.atInfo().log("Enregistre le fichier {} (url: {}) ...", f, urlFile);
                                                            FileUtils.copyURLToFile(
                                                                    new URL(urlFile),
                                                                    f.toFile(),
                                                                    Math.toIntExact(github.getConnexionTimeout().toMillis()),
                                                                    Math.toIntExact(github.getReadTimeout().toMillis()));
                                                            LOGGER.atInfo().log("Enregistre le fichier {} OK", f);
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                    }
                                }
                            }
                        } catch (IOException e) {
                            throw new JBackupException("Erreur pour enregistre le release", e);
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
        LOGGER.atInfo().log("Enregistrement release ok");
    }

}
