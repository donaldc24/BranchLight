package com.branchlight.backend.search.api;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.branchlight.backend.search.domain.SearchResult;
import com.branchlight.backend.search.domain.SearchRole;
import com.branchlight.backend.search.service.SearchService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    @Test
    void delegatesValidQueriesAndMapsTheResponse() throws Exception {
        var query = "  virtual threads  ";
        when(searchService.search(query)).thenReturn(List.of(
                new SearchResult(
                        SearchRole.AUTHORITATIVE,
                        "Official Reference",
                        URI.create("https://example.com/reference"),
                        "example.com",
                        "Example snippet",
                        "Represents an official source.",
                        0.95)));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"  virtual threads  \"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query").value(query))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].role").value("AUTHORITATIVE"))
                .andExpect(jsonPath("$.results[0].title").value("Official Reference"))
                .andExpect(jsonPath("$.results[0].url")
                        .value("https://example.com/reference"))
                .andExpect(jsonPath("$.results[0].domain").value("example.com"))
                .andExpect(jsonPath("$.results[0].snippet").value("Example snippet"))
                .andExpect(jsonPath("$.results[0].selectionReason")
                        .value("Represents an official source."))
                .andExpect(jsonPath("$.results[0].score").value(0.95));

        verify(searchService).search(query);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"query\":\"\"}",
            "{\"query\":\"   \"}",
            "{\"query\":null}"
    })
    void rejectsBlankQueriesWithAClearProblemResponse(String requestBody)
            throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid search request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("query must not be blank"));

        verifyNoInteractions(searchService);
    }
}
