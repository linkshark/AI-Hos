package com.linkjb.aimed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
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
