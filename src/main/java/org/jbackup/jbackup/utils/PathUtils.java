package org.jbackup.jbackup.utils;

public class PathUtils {


    public static String getPath(String... path) {
        var s = "";
        if (path != null && path.length > 0) {
            for (var s2 : path) {
                if (s2 != null && s2.length() > 0) {
                    if(s.length()==0){
                        s=s2;
                    } else if (s.endsWith("/") || s.endsWith("\\")) {
                        s += s2;
                    } else if (s2.startsWith("/") || s2.startsWith("\\")) {
                        s += s2;
                    } else {
                        s += "/" + s2;
                    }
                }
            }
        }
        return s;
    }
}
