package org.jbackup.jbackup.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PathUtilsTest {

    @Test
    void getPath() {
        assertEquals("aaa/bbb", PathUtils.getPath("aaa", "bbb"));
        assertEquals("aaa/bbb", PathUtils.getPath("aaa/", "bbb"));
        assertEquals("aaa/bbb", PathUtils.getPath("aaa", "/bbb"));
        assertEquals("bbb", PathUtils.getPath("", "bbb"));
        assertEquals("/bbb", PathUtils.getPath("", "/bbb"));
        assertEquals("bbb", PathUtils.getPath(null, "bbb"));
        assertEquals("/bbb", PathUtils.getPath(null, "/bbb"));
    }

}