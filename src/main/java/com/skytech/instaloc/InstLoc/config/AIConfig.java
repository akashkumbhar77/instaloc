package com.skytech.instaloc.InstLoc.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    @Bean
    public ChatModel chatModel() {
        OpenAiApi openAiApi = new OpenAiApi(openAiApiKey);
        return new OpenAiChatModel(openAiApi);
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt());
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    private String systemPrompt() {
        return """
                You are a location extraction expert. Analyze the provided images from an Instagram Reel and extract all visible or mentioned locations.

                For each location found, provide:
                - name: The official name of the place (e.g., "Eiffel Tower", "Lotte World Tower")
                - category: One of [restaurant, landmark, beach, hotel, bar, cafe, park, museum, shopping, other]
                - confidence: A score from 0.0 to 1.0 indicating how confident you are about this location

                Return ONLY a valid JSON array with no additional text. Format:
                [{"name": "...", "category": "...", "confidence": 0.9}, ...]

                If no locations are found, return an empty array: []
                """;
    }
}
