package org.jbackup.jbackup.utils;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import org.jbackup.jbackup.config.SaveProperties;
import org.jbackup.jbackup.exception.JBackupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;

public class MatchPath {


    private static final Logger LOGGER = LoggerFactory.getLogger(MatchPath.class);

    private Set<String> set = new HashSet<>();

    private final Map<String, PathMatcher> mapGlob = new HashMap<>();

    private static final Splitter SPLITTER = Splitter.on(CharMatcher.is('/').or(CharMatcher.is('\\')))
            .omitEmptyStrings();

    private List<String> excludeList;

    private List<String> excludeGlobList = new ArrayList<>();

    private Map<String, List<String>> map = new HashMap<>();

    public MatchPath(SaveProperties saveProperties) {
        excludeList = saveProperties.getExclude();
        init(saveProperties.getExclude());
    }

    public boolean exclude(Path path) {
        if (false) {
            /*if (!CollectionUtils.isEmpty(excludeList)) {
                for (var glob : excludeList) {
                    try {
                        PathMatcher pathMatcher = getGlob(glob);
                        if (pathMatcher.matches(path)) {
                            return true;
                        }
                    } catch (Exception e) {
                        throw new JBackupException("Error for glob : " + glob, e);
                    }
                }
            }
            return false;*/
            return aExclure(path, excludeList);
        } else {
            var s = path.toString();
            var tab = SPLITTER.splitToList(s);
            if (!tab.isEmpty()) {
                if (set.contains(tab.getLast())) {
                    return true;
                }
                if(map.containsKey(tab.getLast())) {
                    var parcourtComplet=false;
                    var comparaisonInvalide=false;
                    var liste=map.get(tab.getLast());
                    if(tab.size()>=liste.size()) {
                        int i = -1;
                        for (i = 0;i<tab.size(); i++) {
                            var a=tab.get(tab.size()-i-1);
                            var b=liste.get(liste.size()-i-1);
                            if(a==null||b==null||!Objects.equals(a,b)) {
                                comparaisonInvalide=true;
                                break;
                            }
                        }
                        if(!comparaisonInvalide&&i==tab.size()) {
                            return true;
                        }
                    }
                }
            }
            /*if(tab.size()==1){
                set.add(tab.get(0));
            } else if(tab.size()==2&& Objects.equals(tab.get(0),"**")&&!tab.get(1).contains("*")
                    &&!tab.get(1).contains("?")) {
                set.add(tab.get(1));
            } else if(tab.size()==3&& Objects.equals(tab.get(0),"**")&&!tab.get(1).contains("*")
                    &&!tab.get(1).contains("?")&&Objects.equals(tab.get(2),"**")) {
                set.add(tab.get(1));
            }*/
            return aExclure(path, excludeList);
        }
    }

    private boolean aExclure(Path path, List<String> excludeList) {
        if (!CollectionUtils.isEmpty(excludeList)) {
            for (var glob : excludeList) {
                try {
                    PathMatcher pathMatcher = getGlob(glob);
                    if (pathMatcher.matches(path)) {
                        return true;
                    }
                } catch (Exception e) {
                    throw new JBackupException("Error for glob : " + glob, e);
                }
            }
        }
        return false;
    }

    private void init(List<String> exclude) {

        LOGGER.atInfo().log("liste exclusion: {}", exclude);

        for (String s : exclude) {
            var tab = SPLITTER.splitToList(s);
            if (tab.size() == 1) {
                set.add(tab.getFirst());
            } else if (tab.size() == 2 && Objects.equals(tab.get(0), "**") && !tab.get(1).contains("*")
                    && !tab.get(1).contains("?")) {
                set.add(tab.get(1));
            } else if (tab.size() == 3 && Objects.equals(tab.get(0), "**") && !tab.get(1).contains("*")
                    && !tab.get(1).contains("?") && Objects.equals(tab.get(2), "**")) {
                set.add(tab.get(1));
            } else {
                var pasTrouve = true;
                if (!tab.isEmpty()) {
                    List<String> liste=tab;
                    if (tab.getFirst().equals("**")) {
                        liste = liste.subList(1, liste.size() - 1);
                    }
                    if(tab.getLast().equals("**")) {
                        liste = liste.subList(0, liste.size()-2);
                    }
                    if(!liste.isEmpty()){
                        var trouve = false;
                        for (int i = 0; i < liste.size(); i++) {
                            if (liste.get(i).contains("*") || liste.get(i).contains("?")) {
                                trouve = true;
                                break;
                            }
                        }
                        if (!trouve) {
                            map.put(tab.getLast(), liste);
                            pasTrouve = false;
                        }
                    }
                }
                if (pasTrouve) {
                    excludeGlobList.add(s);
                }
            }
        }
        LOGGER.atInfo().log("liste exclusion set: {}", set);
        LOGGER.atInfo().log("liste exclusion map: {}", map);
        LOGGER.atInfo().log("liste exclusion excludeGlobList: {}", excludeGlobList);
    }

    private PathMatcher getGlob(String glob) {
        if (mapGlob.containsKey(glob)) {
            return mapGlob.get(glob);
        } else {
            PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            mapGlob.put(glob, pathMatcher);
            return pathMatcher;
        }
    }
}
