package com.langchain4j.chathealth.configuration.store.azure;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuração do Azure Blob Storage
 * Utiliza variáveis de ambiente em vez de arquivo .env
 */
@Configuration
public class AzureStorageConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AzureStorageConfiguration.class);

    @Value("${azure.storage.connection-string:#{null}}")
    private String connectionString;

    @Bean
    public BlobServiceClient blobServiceClient() {
        // Tentar obter a connection string de variáveis de ambiente
        String connStr = connectionString;
        
        if (connStr == null || connStr.isEmpty()) {
            // Fallback para variável de ambiente do sistema
            connStr = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
        }
        
        if (connStr == null || connStr.isEmpty()) {
            logger.warn("⚠️ AZURE_STORAGE_CONNECTION_STRING não configurada. Usando cliente sem autenticação.");
            // Retornar um cliente vazio para não quebrar o startup
            return new BlobServiceClientBuilder()
                    .buildClient();
        }
        
        logger.info("✅ Configurando Azure Blob Storage com connection string");
        return new BlobServiceClientBuilder()
                .connectionString(connStr)
                .buildClient();
    }
}
