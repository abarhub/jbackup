package org.jbackup.jbackup.service;

import org.jbackup.jbackup.shadowcopy.ShadowCopy;

import java.util.Objects;

public class FactoryService {

    private final RunService runService;

    public FactoryService(RunService runService) {
        this.runService = Objects.requireNonNull(runService);
    }

    public ShadowCopy getShadowService(LinkService linkService){
        return new ShadowCopy(runService, linkService);
    }

    public LinkService linkService(){
        return new LinkService();
    }

}
