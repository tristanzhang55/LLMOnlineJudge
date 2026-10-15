    package com.tfzhang.newai;

    import dev.langchain4j.data.document.Document;
    import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
    import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
    import dev.langchain4j.data.segment.TextSegment;
    import dev.langchain4j.model.embedding.EmbeddingModel;
    import dev.langchain4j.rag.content.retriever.ContentRetriever;
    import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
    import dev.langchain4j.store.embedding.EmbeddingStore;
    import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
    import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
    import jakarta.annotation.Resource;

    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;

    import java.util.List;

    @Configuration
    public class RAGConfig {
        @Resource
        private EmbeddingModel qwenEmbeddingModel;

//        @Resource
//        private EmbeddingStore<TextSegment> embeddingStore;

        @Bean
        public EmbeddingStore<TextSegment> embeddingStore() {
            return new InMemoryEmbeddingStore<>();
        }

        @Bean
        public ContentRetriever contentRetriever() {
            //1. load docs;
            List<Document> documents = FileSystemDocumentLoader.loadDocuments("src/main/resources/docs");

            //2. split docs into segments and store them in embeddingStore
            DocumentByParagraphSplitter paragraphSplitter = new DocumentByParagraphSplitter(1000,200);

            //3. store segments in embeddingStore
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(paragraphSplitter)
                    .textSegmentTransformer(textSegment -> TextSegment.from(
                            textSegment.metadata().getString("file_name")+"\n"+ textSegment.text(),
                            textSegment.metadata()
                    ))
                    .embeddingModel(qwenEmbeddingModel)
                    .embeddingStore(embeddingStore())
                    .build();

            //load documents into embeddingStore
            ingestor.ingest(documents);

            //4. create content retriever
            return EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore())
                    .embeddingModel(qwenEmbeddingModel)
                    .maxResults(5)
                    .minScore(0.75)
                    .build();
        }
    }
