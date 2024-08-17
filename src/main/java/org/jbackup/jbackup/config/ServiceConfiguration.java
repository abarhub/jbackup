package org.jbackup.jbackup.config;

import org.jbackup.jbackup.properties.JBackupProperties;
import org.jbackup.jbackup.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceConfiguration {

    @Bean
    public BackupGithubService backupGithubService(DataService dataService) {
        return new BackupGithubService(dataService);
    }

    @Bean
    public BackupService backupService(JBackupProperties jBackupProperties,
                                       BackupGithubService backupGithubService,
                                       RunService runService, DataService dataService,
                                       FactoryService factoryService) {
        return new BackupService(jBackupProperties, backupGithubService,
                runService, dataService, factoryService);
    }

    @Bean
    public RunService runService() {
        return new RunService();
    }

    @Bean
    public DataService dataService(JBackupProperties jBackupProperties) {
        return new DataService(jBackupProperties);
    }

    @Bean
    public FactoryService factoryService(RunService runService) {
        return new FactoryService(runService);
    }
}
