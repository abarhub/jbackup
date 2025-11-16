package org.jbackup.jbackup.config;

import org.apache.commons.lang3.StringUtils;
import org.jbackup.jbackup.properties.GithubProperties;
import org.jbackup.jbackup.properties.JBackupProperties;
import org.jbackup.jbackup.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class ServiceConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceConfiguration.class);

    @Bean
    public BackupGithubService backupGithubService(DataService dataService, GithubService getGithubService) {
        return new BackupGithubService(dataService, getGithubService);
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

    @Bean
    public GithubService getGithubService(JBackupProperties jBackupProperties, WebClient.Builder webClientBuilder) {
        GithubProperties github=jBackupProperties.getGithub();
        final int size = Math.toIntExact(github.getMaxMemory().toBytes());
        final ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(size))
                .build();
        var exchanged = webClientBuilder
                .baseUrl(github.getGithubUrl())
                .exchangeStrategies(strategies);
        if (StringUtils.isNotBlank(github.getToken())) {
            LOGGER.atInfo().log("api github with token");
            exchanged = exchanged.defaultHeader("Authorization", "Bearer " + github.getToken());
        }
        if (StringUtils.isNotBlank(github.getApiVersion())) {
            LOGGER.atInfo().log("api github version: {}", github.getApiVersion());
            exchanged = exchanged.defaultHeader("X-GitHub-Api-Version", github.getApiVersion());
        }
        WebClient client = exchanged.build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(WebClientAdapter.create(client)).build();

        return factory.createClient(GithubService.class);
    }
}
