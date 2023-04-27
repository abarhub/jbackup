package org.jbackup.jbackup.compress;

import java.nio.file.Path;

public interface CompressTask extends Compress{

    void task(Path p);
}
