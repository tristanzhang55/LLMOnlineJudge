package com.tfzhang.newai.mcp;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class McpConfig {

    @Bean
    public McpToolProvider mcpWebSearchTool(){
        // 1. Start the DuckDuckGo MCP server as a local subprocess via stdio
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of("uvx", "duckduckgo-mcp-server"))
                .logEvents(true) // helpful for debugging the handshake
                .build();

        // 2. Create an MCP client that communicates with the server
        McpClient mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .build();


        // 3. Wrap it as a tool provider LangChain4j's AiServices can use
        McpToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(List.of(mcpClient))
                .build();

        return toolProvider;
    }

}
