package org.jbackup.jbackup.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Verify;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jbackup.jbackup.data.GithubData;
import org.jbackup.jbackup.domain.Project;
import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.properties.GithubProperties;
import org.jbackup.jbackup.utils.RunProgram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    private int nbGitWiki;
    private int nbGitWikiKO;

    private GithubProperties github;

    private final DataService dataService;

    public BackupGithubService(DataService dataService) {
        this.dataService = dataService;
    }

    public void backup(GithubProperties github) {
        var debut = Instant.now();
        this.github = github;
        final var githubService = getGithubService(github);

        final Instant instant = Instant.now();
        LOGGER.atInfo().log("date: {} ({}s)", instant, instant.getEpochSecond());

        enregistreUser(githubService, github, instant);
        enregistreEtoiles(githubService, github, instant);
        nettoyageUserEtoiles();

        enregistreProjets(github, githubService, instant);

        enregistreGist(githubService, github, instant);

        LOGGER.atInfo().log("nb appels: {} (user:{}, repos:{}, started:{}, " +
                        "gist: {}, release: {}, git repos: {}, git gist: {}, git wiki: {}, git wiki ok: {})",
                nbGetUser + nbGetRepos + nbGetStarred + nbGetGist + nbGetRelease,
                nbGetUser, nbGetRepos, nbGetStarred, nbGetGist, nbGetRelease,
                nbGitRepo, nbGitGist, nbGitWiki, nbGitWikiKO);

        LOGGER.atInfo().log("duree du backup: {}", Duration.between(debut, Instant.now()));
    }

    private void nettoyageUserEtoiles() {
        if (github.getNettoyagePeriode() == null || github.getNettoyagePeriode().isZero()
                || github.getNettoyagePeriode().isNegative()) {
            LOGGER.atInfo().log("Pas de nettoyage des users et des etoiles");
        } else {
            try {
                nettoyage_users();

                nettoyage_star();

            } catch (IOException e) {
                LOGGER.error("Erreur pour faire le nettoyage", e);
            }
        }
    }

    private void nettoyage_users() throws IOException {
        LOGGER.atInfo().log("Nettoyage users (nbBackup:{}, periode:{})...",
                github.getNettoyageConcervationMin(), github.getNettoyagePeriode());
        var rep = Paths.get(github.getDest(), "github_user");

        final var debut = "github_user_";
        final var fin = ".json";
        try (var stream = Files.list(rep)) {

            var listeFichiers = stream
                    .filter(Files::isRegularFile)
                    .filter(x -> x.getFileName().toString().startsWith(debut)
                            && x.getFileName().toString().endsWith(fin))
                    .toList();

            Map<LocalDate, List<Path>> map = new TreeMap<>();
            listeFichiers.forEach(path -> {
                var filename = path.getFileName().toString();
                var s = StringUtils.substringBetween(filename, debut, fin);
                if (s != null) {
                    LocalDate date = null;
                    try {
                        long n = Long.parseLong(s);
                        Instant instant = Instant.ofEpochSecond(n);
                        date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
                    } catch (NumberFormatException e) {
                        LOGGER.atError().log("Erreur pour trouver la date: {}", s, e);
                    }
                    if (date != null) {
                        map.computeIfAbsent(date, k -> new ArrayList<>()).add(path);
                    }
                }
            });
            suppressionFichierTropAncien(map);
        }
        LOGGER.atInfo().log("Nettoyage users OK");
    }

    private void suppressionFichierTropAncien(Map<LocalDate, List<Path>> map) throws IOException {
        List<LocalDate> dates = new ArrayList<>(map.keySet());
        Collections.sort(dates);
        Collections.reverse(dates);
        List<Path> fichiersASupprimer = new ArrayList<>();
        int nb = 0;
        LocalDate dateMax = LocalDateTime.now().minus(github.getNettoyagePeriode()).toLocalDate();
        Verify.verify(dateMax.isBefore(LocalDate.now()));
        for (LocalDate date : dates) {
            if (date.isBefore(dateMax) && nb >= github.getNettoyageConcervationMin()) {
                fichiersASupprimer.addAll(map.get(date));
            }
            nb++;
        }
        LOGGER.atInfo().log("nb fichiers à supprimer : {}", fichiersASupprimer.size());
        for (int i = 0; i < fichiersASupprimer.size(); i++) {
            LOGGER.atInfo().log("suppression de : {}", fichiersASupprimer.get(i));
            Files.deleteIfExists(fichiersASupprimer.get(i));
        }
    }

    private void nettoyage_star() throws IOException {
        LOGGER.atInfo().log("Nettoyage star ...");

        var rep = Paths.get(github.getDest(), "starred");
        //var path2 = rep.resolve("starred_" + instant.getEpochSecond() + "_" + no + ".json");

        LOGGER.atInfo().log("compression");

        final var debut = "starred_";
        final var fin = ".json";

        try (var stream = Files.list(rep)) {
            var listeFichiers = stream
                    .filter(Files::isRegularFile)
                    .filter(x -> x.getFileName().toString().startsWith(debut)
                            && x.getFileName().toString().endsWith(fin))
                    .toList();

            Map<LocalDate, List<Path>> map = new TreeMap<>();

            listeFichiers.forEach(path -> {
                var filename = path.getFileName().toString();
                var s = StringUtils.substringBetween(filename, debut, fin);
                if (s != null) {
                    LocalDate date = null;
                    try {
                        var s2 = StringUtils.substringBefore(s, "_");
                        long n = Long.parseLong(s2);
                        Instant instant = Instant.ofEpochSecond(n);
                        date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
                    } catch (NumberFormatException e) {
                        LOGGER.atError().log("Erreur pour trouver la date: {}", s, e);
                    }
                    if (date != null) {
                        map.computeIfAbsent(date, k -> new ArrayList<>()).add(path);
                    }
                }
            });

            for (var entry : map.entrySet()) {

                LocalDate date = entry.getKey();
                List<Path> fichiers = entry.getValue();
                Path p = fichiers.getFirst().getParent();
                DateTimeFormatter formatter = DateTimeFormatter.BASIC_ISO_DATE;
                String formattedString = date.format(formatter);
                Path f = p.resolve(debut + formattedString + ".zip");
                if (Files.exists(f)) {
                    int n = 2;
                    while (Files.exists(f)) {
                        f = p.resolve(debut + formattedString + "_" + n + ".zip");
                        n++;
                    }
                }

                zipFiles(f, fichiers);
                for (var fichier : fichiers) {
                    LOGGER.atInfo().log("suppression de : {}", fichier);
                    Files.deleteIfExists(fichier);
                }
            }

        }

        LOGGER.atInfo().log("nettoyage");

        final var fin2 = ".zip";

        try (var stream = Files.list(rep)) {
            var listeFichiers = stream
                    .filter(Files::isRegularFile)
                    .filter(x -> x.getFileName().toString().startsWith(debut)
                            && x.getFileName().toString().endsWith(fin2))
                    .toList();

            Map<LocalDate, List<Path>> map = new TreeMap<>();

            listeFichiers.forEach(path -> {
                var filename = path.getFileName().toString();
                var s = StringUtils.substringBetween(filename, debut, fin2);
                if (s != null) {
                    LocalDate date = null;
                    try {
                        var s2 = StringUtils.substringBefore(s, "_");
                        if (s2 != null) {
                            s = s2;
                        }
                        DateTimeFormatter formatter = DateTimeFormatter.BASIC_ISO_DATE;
                        date = LocalDate.parse(s, formatter);
                    } catch (NumberFormatException e) {
                        LOGGER.atError().log("Erreur pour trouver la date: {}", s, e);
                    }
                    if (date != null) {
                        map.computeIfAbsent(date, k -> new ArrayList<>()).add(path);
                    }
                }
            });

            suppressionFichierTropAncien(map);
        }

        LOGGER.atInfo().log("Nettoyage star OK");
    }

    private void zipFiles(Path f, List<Path> fichiers) throws IOException {
        LOGGER.atInfo().log("Zipping files to {} ...", f);
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(f))) {

            for (var srcFile : fichiers) {
                File fileToZip = srcFile.toFile();
                try (FileInputStream fis = new FileInputStream(fileToZip)) {
                    ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
                    zipOut.putNextEntry(zipEntry);

                    byte[] bytes = new byte[1024];
                    int length;
                    while ((length = fis.read(bytes)) >= 0) {
                        zipOut.write(bytes, 0, length);
                    }
                }
            }

            //zipOut.close();
            //fos.close();
        }
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
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(WebClientAdapter.create(client)).build();

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
                var res = updateGit(rep, projet.getNom(), projet.getCloneUrl(), github.isMirror(),
                        github.isUpdateReposByDate(), projet);
                nbGitRepo++;
                if (!res) {
                    LOGGER.atError().log("Erreur pour mettre à jour le reporitory {}", rep);
                    throw new JBackupException("Erreur pour mettre à jour le reporitory " + rep);
                }

                enregistreReleases(githubService, github, instant, projet);
                if (projet.isHasWiki()) {
                    enregistreWiki(github, projet);
                }
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
                                var wiki = project.get("has_wiki").asBoolean(false);
                                var createAt = convertLocalDateTime(project.get("created_at").asText());
                                var updatedAt = convertLocalDateTime(project.get("updated_at").asText());
                                var pushedAt = convertLocalDateTime(project.get("pushed_at").asText());
                                var projet = new Project();
                                projet.setNom(nom);
                                projet.setUrl(url);
                                projet.setCloneUrl(urlClone);
                                projet.setHasWiki(wiki);
                                projet.setCreatedAt(createAt);
                                projet.setUpdatedAt(updatedAt);
                                projet.setPushedAt(pushedAt);
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

    private LocalDateTime convertLocalDateTime(String dateStr) {
        if (StringUtils.isNotBlank(dateStr)) {
            var res = ZonedDateTime.parse(dateStr).toLocalDateTime();
            LOGGER.atDebug().log("dateStr:{}, date:{}", dateStr, res);
            return res;
        } else {
            return null;
        }
    }

    private void listeProjetsDifferents(List<Project> listeProjet, Path pathRoot) {
        try (var listeFiles = Files.list(pathRoot)) {
            var listeRemote = listeProjet.stream().map(Project::getNom).toList();
            var listeLocal = listeFiles.map(x -> x.getFileName().toString()).toList();
            LOGGER.atDebug().log("liste des projets en remote : {}", listeRemote);
            LOGGER.atDebug().log("liste des projets en local : {}", listeLocal);
            Set<String> liste1 = new TreeSet<>(listeRemote);
            listeLocal.forEach(liste1::remove);
            Set<String> liste2 = new TreeSet<>(listeLocal);
            listeRemote.forEach(liste2::remove);
            LOGGER.atInfo().log("liste des projets en remote en trop: {}", liste1);
            LOGGER.atInfo().log("liste des projet en local en trop: {}", liste2);
        } catch (IOException e) {
            LOGGER.atError().log("Erreur pour liste les projets", e);
        }
    }

    private boolean updateGit(Path rep, String nom, String cloneUrl, boolean mirror,
                              boolean updateReposByDate, Project projet) {
        List<String> tab, tab2;
        final var debut = "https://";
        var cloneUrlShow = cloneUrl;
        if (cloneUrl.startsWith(debut)) {
            final var cloneUrl0 = cloneUrl;
            cloneUrl = cloneUrl0.substring(0, debut.length()) + github.getUser() + ":" + github.getToken() + "@" + cloneUrl0.substring(debut.length());
            cloneUrlShow = cloneUrl0.substring(0, debut.length()) + github.getUser() + ":XXX@" + cloneUrl0.substring(debut.length());
        }
        if (mirror) {

            LOGGER.atInfo().log("clone mirror {} ({})", nom, cloneUrlShow);
            tab = List.of("git", "clone", "--mirror", cloneUrl, rep.toString());
            tab2 = List.of("git", "clone", "--mirror", cloneUrlShow, rep.toString());
        } else {
            if (Files.exists(rep)) {
                LOGGER.atInfo().log("pull {}", nom);
                tab = List.of("git", "-C", rep.toString(), "pull", "--all");
                tab2 = tab;
                if (updateReposByDate && projet != null) {
                    LocalDateTime lastDateCommit = null;
                    LocalDateTime lastDateTraitement = null;
                    if (projet.getPushedAt() != null) {
                        lastDateCommit = projet.getPushedAt();
                    }
                    if (projet.getUpdatedAt() != null && (lastDateCommit == null || lastDateCommit.isBefore(projet.getUpdatedAt()))) {
                        lastDateCommit = projet.getUpdatedAt();
                    }
                    if (projet.getCreatedAt() != null && (lastDateCommit == null || lastDateCommit.isBefore(projet.getCreatedAt()))) {
                        lastDateCommit = projet.getCreatedAt();
                    }
                    var map = dataService.getjBackupData().getListeGithub();
                    if (map != null && map.containsKey(nom)) {
                        lastDateTraitement = map.get(nom).getDate();
                    }
                    if (lastDateTraitement != null && lastDateCommit != null && lastDateCommit.isBefore(lastDateTraitement)) {
                        LOGGER.atInfo().log("pas de pull pour {}, car pas de mise à jour " +
                                        "(dernier commit: {}, dernier import: {})",
                                nom, lastDateCommit, lastDateTraitement);
                        return true;
                    }
                }
            } else {
                LOGGER.atInfo().log("clone {} ({})", nom, cloneUrlShow);
                tab = List.of("git", "clone", cloneUrl, rep.toString());
                tab2 = List.of("git", "clone", cloneUrlShow, rep.toString());
            }
        }
        try {
            RunProgram runProgram = new RunProgram();
            var res = runProgram.runCommand(true, tab, tab2);
            if (res != 0) {
                LOGGER.error("Erreur res={}", res);
                return false;
            } else {
                LOGGER.info("res={}", res);
                var map = dataService.getjBackupData().getListeGithub();
                if (map.containsKey(nom)) {
                    map.get(nom).setDate(LocalDateTime.now());
                } else {
                    GithubData data = new GithubData();
                    data.setNom(nom);
                    data.setDate(LocalDateTime.now());
                    map.put(nom, data);
                }
                return true;
            }
        } catch (IOException | InterruptedException e) {
            throw new JBackupException("Erreur pour executer la commande git " + tab + " vers " + rep, e);
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
        JsonNode root = null;
        LOGGER.atInfo().log("Enregistrement des étoiles ...");
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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

                        if (StringUtils.isNotBlank(body)) {
                            try {
                                JsonNode actualObj = mapper.readTree(body);
                                if (actualObj != null && actualObj.isArray() && !actualObj.isEmpty()) {
                                    aucuneDonnees = false;
                                    if (root == null) {
                                        root = actualObj;
                                    } else {
                                        if (root instanceof ArrayNode && actualObj instanceof ArrayNode) {
                                            ((ArrayNode) root).addAll((ArrayNode) actualObj);
                                        } else {
                                            Assert.isTrue(root.isArray(), "root n'est pas un tableau");
                                            Assert.isTrue(actualObj.isArray(), "actualObj n'est pas un tableau");
                                        }
                                    }
                                }

                            } catch (JsonProcessingException e) {
                                LOGGER.atError().log("Erreur pour ce body: {}", body);
                                throw new JBackupException("Erreur pour parser le flux", e);
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
        if (root != null) {
            var rep = Paths.get(github.getDest(), "starred");
            try {
                Files.createDirectories(rep);
                no = 0;
                var path2 = rep.resolve("starred_" + instant.getEpochSecond() + "_" + no + ".json");
                var body = mapper.writeValueAsString(root);
                enregistreJsonSiNonVide(path2, body);
            } catch (IOException e) {
                throw new JBackupException("Erreur pour enregistre les informations de l'utilisateur", e);
            }
        }
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
                                            var res = updateGit(rep2, id, urlPull, github.isMirror(), false, null);
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
        try {
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
                                                                        URI.create(urlFile).toURL(),
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
        } catch (WebClientResponseException e) {
            LOGGER.atError().log("Erreur pour récupérer la liste des releases (code http:{}) :  {}", e.getStatusCode(), e.getMessage(), e);
        }
    }

    private void enregistreWiki(GithubProperties github, Project projet) {
        LOGGER.atInfo().log("Enregistrement wiki ...");
        var rep = Paths.get(github.getDest(), "wiki", projet.getNom());
        var path = rep.resolve("wiki");
        var url = projet.getCloneUrl();
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4) + ".wiki.git";
            LOGGER.atInfo().log("clonage du wiki {} ({},{}) ...", projet.getNom(), url, path);
            var res = updateGit(path, projet.getNom(), url, false, false, projet);
            if (!res) {
                nbGitWikiKO++;
                LOGGER.atWarn().log("Le clonage a echouer pour le wiki du projet {}", projet.getNom());
                try {
                    LOGGER.atInfo().log("Suppression du répertoire {} si il est vide ...", path.getParent());
                    Files.deleteIfExists(path);
                    Files.deleteIfExists(path.getParent());
                    LOGGER.atInfo().log("Suppression du répertoire {} ok", path);
                } catch (IOException e) {
                    LOGGER.atWarn().log("Erreur pour supprimer le répertoire {}", path, e);
                }
            } else {
                nbGitWiki++;
                LOGGER.atInfo().log("clonage du wiki {} ok", projet.getNom());
            }
        }
        LOGGER.atInfo().log("Enregistrement wiki ok");
    }

}
