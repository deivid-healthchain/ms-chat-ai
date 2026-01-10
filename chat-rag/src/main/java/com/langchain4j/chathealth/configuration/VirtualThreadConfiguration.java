package com.langchain4j.chathealth.configuration;

import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuração de Virtual Threads para Java 21
 * Permite que o Tomcat use Virtual Threads para melhor escalabilidade
 */
@Configuration
public class VirtualThreadConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadConfiguration.class);

    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        logger.info("🚀 Configurando Virtual Threads para Tomcat");
        
        return protocolHandler -> {
            // Usar Virtual Threads para o executor do Tomcat
            // Isso permite que cada requisição seja processada em uma Virtual Thread
            // em vez de usar thread pool tradicional
            protocolHandler.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        };
    }
}
