package com.example.demo.client.google;

import com.example.demo.exception.GoogleBookRetrievalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class GoogleBookService {
    private final RestClient restClient;

    public GoogleBookService(@Value("${google.books.base-url:https://www.googleapis.com/books/v1}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public GoogleBook searchBooks(String query, Integer maxResults, Integer startIndex) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/volumes")
                        .queryParam("q", query)
                        .queryParam("maxResults", maxResults != null ? maxResults : 10)
                        .queryParam("startIndex", startIndex != null ? startIndex : 0)
                        .build())
                .retrieve()
                .body(GoogleBook.class);
    }

    public GoogleBook.Item getBookById(String googleId) {
        try {
            return restClient.get()
                    .uri("/volumes/{id}", googleId)
                    .retrieve()
                    .body(GoogleBook.Item.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                log.error("Google Books API returned 404 Not Found for id={}", googleId);
                throw new GoogleBookRetrievalException("Book not found in Google Books API");
            }
            log.error("Google Books API returned error status {} for id={}: {}", e.getStatusCode(), googleId,
                    e.getMessage());
            throw new ResponseStatusException(e.getStatusCode(), "Failed to fetch book from Google Books API");
        } catch (Exception e) {
            log.error("Unexpected error fetching book from Google Books API for id={}: {}", googleId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error fetching book from Google Books API");
        }
    }
}
