package com.linkjb.aimed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class MainApp {
    static {
        // Keep the app-wide default transport explicit once multiple LangChain4j HTTP clients are on the classpath.
        System.setProperty(
                "langchain4j.http.clientBuilderFactory",
                "dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilderFactory"
        );
    }

    public static void main(String[] args) {
        SpringApplication.run(MainApp.class, args);
    }
}
