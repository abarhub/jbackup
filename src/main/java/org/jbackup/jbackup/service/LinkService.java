package org.jbackup.jbackup.service;

import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.shadowcopy.ShadowPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LinkService implements AutoCloseable{

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkService.class);

    private final Map<Path, Path> mapLink = new ConcurrentHashMap<>();

    private final AtomicInteger counter=new AtomicInteger(1);

    public Path getLink(ShadowPath p, Path path) {
        return getLink(p.volume(),p.path(),path);
    }

    public Path getLink(char volume, Path p, Path path) {
        var s = path.toString();
        var s2 = p.toString();
        s = s.substring(2);
        var s3 = s2 + s;
//        if(s3.startsWith("\\\\?\\")){
//            s3="\\\\.\\"+s3.substring(4);
//        }
        Path p3;
        if (!mapLink.containsKey(path)) {
            var linkDebut = volume + ":/linkjb";
            var link = linkDebut;
            var i = counter.getAndIncrement();
            if (i > 1) {
                link += i;
            }
            while (Files.exists(Path.of(link), LinkOption.NOFOLLOW_LINKS)) {
                LOGGER.atInfo().log("lien {} existe", link);
                i = counter.getAndIncrement();
                link = linkDebut + i;
            }
            LOGGER.atInfo().log("lien {} n'existe pas (exist:({}, {}), not_exist:({}, {}))",
                    link,
                    Files.exists(Path.of(link)),
                    Files.exists(Path.of(link), LinkOption.NOFOLLOW_LINKS),
                    Files.notExists(Path.of(link)),
                    Files.notExists(Path.of(link), LinkOption.NOFOLLOW_LINKS));
            var linkp = Path.of(link);
            var cible = Path.of(s3).getParent();
            LOGGER.info("création du lien '{}' -> '{}'", linkp, cible);
            try {
                LOGGER.info("création du lien '{}' ...", linkp);
                Files.createSymbolicLink(linkp, cible);
                LOGGER.info("création du lien '{}' ok", linkp);
            } catch (IOException e) {
                throw new JBackupException("Erreur pour creer le lien '" + linkp + "' -> '" + cible + "' : " + e.getMessage(), e);
            }
            p3 = linkp.resolve(Path.of(s3).getFileName());
            mapLink.put(path, p3);
        } else {
            p3 = mapLink.get(path);
        }
//        return Path.of(s3);
        return p3;
    }

    public void close(){
        for (var entry : mapLink.entrySet()) {
            var path = entry.getValue().getParent();
            try {
                LOGGER.info("Suppression du link {} ...", path);
                //var deletedIfExists = Files.deleteIfExists(entry.getValue());
//                Files.delete(path);
                var deletedIfExists = Files.deleteIfExists(path);
                LOGGER.info("Suppression du link {} OK (deleted={})", path, deletedIfExists);
            } catch (IOException e) {
                LOGGER.error("Can't delete link for {}", path, e);
            }
        }
    }
}
