package org.jbackup.jbackup.compress;

import java.nio.file.Path;

public interface Compress extends AutoCloseable{

    void start();

    void close();

    void addFile(String name, Path p);

}
