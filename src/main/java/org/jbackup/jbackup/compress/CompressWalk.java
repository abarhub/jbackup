package org.jbackup.jbackup.compress;

import java.nio.file.Path;

public interface CompressWalk extends Compress{

    void addFile(String name, Path p);

    void addDir(String name, Path p);

}
