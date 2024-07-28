package org.jbackup.jbackup.config;

import org.jbackup.jbackup.properties.JBackupProperties;
import org.jbackup.jbackup.service.BackupGithubService;
import org.jbackup.jbackup.service.BackupService;
import org.jbackup.jbackup.service.RunService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceConfiguration {

    @Bean
    public BackupGithubService backupGithubService() {
        return new BackupGithubService();
    }

    @Bean
    public BackupService backupService(JBackupProperties jBackupProperties,
                                       BackupGithubService backupGithubService,
                                       RunService runService) {
        return new BackupService(jBackupProperties, backupGithubService, runService);
    }

    @Bean
    public RunService runService(){
        return new RunService();
    }
}
