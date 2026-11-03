package com.crawler.api;

import com.crawler.api.dto.CrawlRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CrawlerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRejectInvalidSeedUrl() throws Exception {
        CrawlRequestDto dto = new CrawlRequestDto("not-a-valid-url");

        mockMvc.perform(post("/api/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnInitialStats() throws Exception {
        mockMvc.perform(get("/api/crawler/stats?topK=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRunning").value(false))
                .andExpect(jsonPath("$.totalPagesCrawled").isNumber());
    }
}