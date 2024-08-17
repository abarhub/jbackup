package org.jbackup.jbackup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JbackupApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(JbackupApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.run(args);
	}

}
