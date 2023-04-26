package org.jbackup.jbackup.shadowcopy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TeeList {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeeList.class);

    private final boolean logInfo;

    private List<String> list = new CopyOnWriteArrayList<>();

    public TeeList(boolean logInfo) {
        this.logInfo = logInfo;
    }

    public void add(String s) {
        list.add(s);
        if(logInfo) {
            LOGGER.atInfo().log(s);
        } else {
            LOGGER.atDebug().log(s);
        }
    }

    public List<String> getList() {
        return list;
    }
}
