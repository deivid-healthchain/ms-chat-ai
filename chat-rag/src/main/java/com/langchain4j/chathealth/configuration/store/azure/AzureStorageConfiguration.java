package com.langchain4j.chathealth.configuration.store.azure;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureStorageConfiguration {

    @Bean
    public BlobServiceClient blobServiceClient() {
        Dotenv dotenv = Dotenv.load();
        return new BlobServiceClientBuilder()
                .connectionString(dotenv.get("AZURE_STORAGE_CONNECTION_STRING"))
                .buildClient();
    }
}