package ge.jar.springaiworkshop.config;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spring AI ETL Pipeline components.
 * Provides document transformers for processing travel data.
 */
@Configuration
public class DocumentProcessingConfig {

    /**
     * Default chunk size for splitting documents.
     * Smaller chunks provide more precise retrieval but lose context.
     * Larger chunks maintain context but may include irrelevant information.
     */
    @Value("${voyagermate.etl.chunk-size:800}")
    private int defaultChunkSize;

    /**
     * Overlap between chunks to maintain context across boundaries.
     */
    @Value("${voyagermate.etl.chunk-overlap:100}")
    private int chunkOverlap;

    /**
     * Creates a TokenTextSplitter for chunking large documents.
     * Used to split travel guides into manageable pieces for processing.
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(defaultChunkSize, chunkOverlap, 5, 10000, true);
    }
}








