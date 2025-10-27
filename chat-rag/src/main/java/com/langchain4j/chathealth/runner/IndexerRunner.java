package com.langchain4j.chathealth.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.langchain4j.chathealth.service.IndexerService;

/**
 * Esta classe é uma tarefa de linha de comando que é executada apenas quando o perfil 'indexer' está ativo.
 * A sua única responsabilidade é acionar o serviço de ingestão de documentos e encerrar a aplicação de forma controlada.
 */
@Profile("indexer") // Garante que este bean só será ativado quando o perfil "indexer" for usado.
@Component      // ESSENCIAL: Transforma esta classe em um bean gerenciado pelo Spring, permitindo que ela seja encontrada e executada.
public class IndexerRunner implements CommandLineRunner {

    private final IndexerService indexerService;
    private final ConfigurableApplicationContext context; // Necessário para encerrar a aplicação de forma limpa.

    /**
     * O Spring injeta automaticamente as dependências necessárias (outros beans)
     * através do construtor.
     * @param indexerService O serviço que contém a lógica de ingestão.
     * @param context O contexto da aplicação Spring.
     */
    public IndexerRunner(IndexerService indexerService, ConfigurableApplicationContext context) {
        // --- LOG DE DEBUG ---
        System.out.println(">>> CONSTRUINDO O BEAN: IndexerRunner (serviço de indexação não é nulo? " + (indexerService != null) + ") <<<");
        // --- FIM DO LOG ---
        this.indexerService = indexerService;
        this.context = context;
    }

    /**
     * Este método é o ponto de entrada da nossa tarefa. Ele é executado automaticamente
     * pelo Spring após a inicialização da aplicação e quando o perfil 'indexer' está ativo.
     * @param args Argumentos de linha de comando (não utilizados neste caso).
     */
    @Override
    public void run(String... args) {
        System.out.println("PERFIL 'indexer' ATIVO: Iniciando processo de ingestão...");

        try {
            // Delega a execução da lógica de negócio para o serviço apropriado.
            indexerService.process();

            System.out.println("INGESTÃO CONCLUÍDA COM SUCESSO!");
            
            // Encerra a aplicação com código de sucesso (0), indicando que o Job foi bem-sucedido.
            System.exit(SpringApplication.exit(context, () -> 0));

        } catch (Exception e) {
            System.err.println("FALHA CRÍTICA NO PROCESSO DE INGESTÃO: " + e.getMessage());
            e.printStackTrace(); // Imprime o stack trace completo do erro para facilitar o debug.

            // Encerra a aplicação com código de erro (1), indicando que o Job falhou.
            System.exit(SpringApplication.exit(context, () -> 1));
        }
    }
}