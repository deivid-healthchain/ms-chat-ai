package com.langchain4j.chathealth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class ChatApplication {
    public static void main(String[] args) {
        // Dotenv dotenv = Dotenv.load();

        try {
            io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            dotenv.entries().forEach(entry -> {
                if (System.getenv(entry.getKey()) == null && System.getProperty(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());

                }
            });
        } catch (Exception e) {
            System.err.println("Erro ao carregar .env: " + e.getMessage());
        }
        SpringApplication.run(ChatApplication.class, args);
    }
}
