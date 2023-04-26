package org.jbackup.jbackup.shadowcopy;

import java.nio.file.Path;

public record ShadowPath(char volume, Path path, String shadowId) {




}
