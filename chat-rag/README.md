
# Chat Healthchain RAG

API e job Java Spring Boot para perguntas e respostas com RAG (Retrieval-Augmented Generation), usando LangChain4j, Azure OpenAI, Azure AI Search e Azure Blob Storage.

---

## Visão Geral

- **Perguntas e respostas**: API REST que responde perguntas com base em PDFs indexados.
- **Ingestão e indexação**: Pipeline automatizado para ingestão de PDFs do Azure Blob Storage, geração de embeddings e indexação vetorial no Azure AI Search.
- **Execução flexível**: Pode rodar como API REST ou como job de indexação (perfil `indexer`), ideal para automação em pipelines e OCP4/Kubernetes.

---

## Principais Recursos

### API REST

- **POST `/api/v1/ask`**  
	Recebe um JSON `{ "pergunta": "..." }` e retorna resposta baseada nos documentos indexados.
	- Se não houver contexto relevante nos embeddings, retorna:  
	  `Nenhum conteúdo relacionado foi encontrado para sua pergunta.`
- **GET `/api/v1/health`**  
	Verifica se a API está ativa.

### Job de Indexação

- **Perfil `indexer`**  
	Executa a ingestão completa dos PDFs do Azure Blob Storage, gera embeddings e reconstrói o índice vetorial no Azure AI Search.  
	Ideal para automação, pipelines CI/CD e clusters Kubernetes/OpenShift.
	- **Ingestão baseada em hash:**
		- O sistema calcula o hash SHA-256 de cada arquivo PDF no Blob Storage.
		- Antes de ingerir, compara o hash do arquivo com o hash já presente no índice.
		- Apenas arquivos novos ou modificados (hash diferente) são processados e indexados.
		- Arquivos removidos do Blob Storage são automaticamente removidos do índice.
		- Isso garante eficiência e evita reprocessamento desnecessário.

---

## Fluxo de Funcionamento

1. **Upload de PDFs**:  
	 Os arquivos PDF são enviados para um container no Azure Blob Storage.

2. **Ingestão/Indexação**:  
	O job de indexação (`IndexerService`) busca todos os PDFs do Blob Storage, calcula o hash de cada arquivo, compara com o índice e:
	- Apenas arquivos novos ou modificados são processados (baseado no hash SHA-256).
	- Arquivos removidos do Blob Storage são removidos do índice.
	- Cada segmento é enriquecido com metadados, embeddings e persistido no Azure AI Search.

3. **Perguntas e Respostas**:  
	 A API REST recebe perguntas, busca contexto relevante no índice vetorial e responde usando Azure OpenAI.
	- Se não houver contexto relevante (score abaixo do limiar), retorna mensagem informando que não há conteúdo relacionado.

---

## Como Executar

### 1. Configuração

- Java 17+
- Maven 3.8+
- Docker (opcional)
- Conta e recursos configurados no Azure (OpenAI, AI Search, Blob Storage)

#### Variáveis de ambiente necessárias

- `AZURE_OPENAI_ENDPOINT`
- `AZURE_OPENAI_KEY`
- `AZURE_SEARCH_ENDPOINT`
- `AZURE_SEARCH_KEY`
- `AZURE_AISEARCH_INDEX_NAME`
- `AZURE_AISEARCH_EMBEDDING_DIMENSION`
- `AZURE_STORAGE_CONNECTION_STRING`
- `AZURE_STORAGE_CONTAINER_NAME`

Exemplo de `.env`:

```env
AZURE_OPENAI_ENDPOINT=https://<seu-endpoint-openai>.openai.azure.com/
AZURE_OPENAI_KEY=chave_aqui
AZURE_SEARCH_ENDPOINT=https://<seu-endpoint-search>.search.windows.net/
AZURE_SEARCH_KEY=chave_aqui
AZURE_AISEARCH_INDEX_NAME=vectorsearch
AZURE_AISEARCH_EMBEDDING_DIMENSION=384
AZURE_STORAGE_CONNECTION_STRING=DefaultEndpointsProtocol=...
AZURE_STORAGE_CONTAINER_NAME=pdfs
```

### 2. Rodando a Aplicação

#### Profiles Disponíveis

A aplicação possui dois profiles principais:

- **default**: Inicia a aplicação como API REST (profile padrão)
- **indexer**: Executa o job de indexação de documentos

#### Usando Maven

```sh
# Executar como API REST (profile default)
mvn spring-boot:run

# Executar como indexador
mvn spring-boot:run -Dspring-boot.run.profiles=indexer
```

#### Usando Java diretamente

```sh
# Executar como API REST (profile default)
java -jar target/chat-health-0.0.1.jar

# Executar como indexador
java -jar target/chat-health-0.0.1.jar --spring.profiles.active=indexer

# Combinar múltiplos profiles (exemplo: indexador + dev)
java -jar target/chat-health-0.0.1.jar --spring.profiles.active=indexer,dev
```

#### Usando Docker

```sh
# Construir a imagem
docker build -t chat-rag .

# Executar o container (substituindo as variáveis de ambiente)
docker run -p 8080:8080 \
  -e AZURE_OPENAI_ENDPOINT=seu_endpoint \
  -e AZURE_OPENAI_KEY=sua_chave \
  -e AZURE_SEARCH_ENDPOINT=seu_endpoint \
  -e AZURE_SEARCH_KEY=sua_chave \
  -e AZURE_AISEARCH_INDEX_NAME=vectorsearch \
  -e AZURE_AISEARCH_EMBEDDING_DIMENSION=384 \
  -e AZURE_STORAGE_CONNECTION_STRING=sua_connection_string \
  -e AZURE_STORAGE_CONTAINER_NAME=pdfs \
  chat-rag

# Ou usando um arquivo .env
docker run -p 8080:8080 --env-file .env chat-rag
```

Acesse:
- `POST /api/v1/ask`
- `GET /api/v1/health`

### 3. Endpoints da API REST

A API estará disponível em `http://localhost:8080` com os seguintes endpoints:

- `POST /api/v1/ask`: Endpoint para fazer perguntas
  ```json
  {
    "message": "Sua pergunta aqui"
  }
  ```
- `GET /api/v1/health`: Verificar status da API

### 4. Usando Docker

```sh
# Usando variáveis de ambiente
docker run --env-file .env chat-rag --spring.profiles.active=indexer

# Ou especificando as variáveis diretamente
docker run \
  -e AZURE_OPENAI_ENDPOINT=seu_endpoint \
  -e AZURE_OPENAI_KEY=sua_chave \
  [outras variáveis...] \
  chat-rag --spring.profiles.active=indexer
```

---

## Estrutura do Projeto

```
├── pom.xml
├── README.md
├── Dockerfile
├── docs/
├── src/
│   └── main/
│       ├── java/com/langchain4j/chathealth/
│       │   ├── ChatController.java
│       │   ├── service/
│       │   │   ├── RagService.java
│       │   │   ├── IndexerService.java
│       │   │   └── rag/RagAssistant.java
│       │   └── configuration/
│       │       └── store/azure/
│       │           ├── AzureEmbeddingStoreConfiguration.java
│       │           └── AzureStorageConfiguration.java
│       └── resources/application.properties
```

---

## Referências

- [LangChain4j](https://docs.langchain4j.dev/)
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Azure OpenAI](https://learn.microsoft.com/azure/ai-services/openai/)
- [Azure AI Search](https://learn.microsoft.com/azure/search/)
- [Azure Blob Storage](https://learn.microsoft.com/azure/storage/blobs/)
```

