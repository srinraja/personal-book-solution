package com.example.demo.client.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke tests that hit the REAL Google Books API.
 * Marked as @Disabled because they are flaky due to external network issues, 
 * rate limiting, or transient 503 errors from Google.
 */
@SpringBootTest
@Disabled("Flaky due to external API dependency")
class GoogleBookServiceSmokeIT {

    @Autowired
    private GoogleBookService googleBookService;

    /**
     * Executes a real external network call to search the live Google Books API for "effective java".
     * Validates that real, non-empty, well-structured JSON maps properly to our local records.
     */
    @Test
    void should_ReturnStructuredResults_When_SearchingEffectiveJava() {
        GoogleBook result = googleBookService.searchBooks("effective+java", 5, 0);
        assertThat(result).isNotNull();
        assertThat(result.kind()).isEqualTo("books#volumes");
        assertThat(result.totalItems()).isGreaterThan(0);
        assertThat(result.items()).isNotNull();
        assertThat(result.items()).isNotEmpty();

        GoogleBook.Item first = result.items().get(0);
        assertThat(first.id()).isNotBlank();
        assertThat(first.selfLink()).isNotBlank();
        assertThat(first.volumeInfo()).isNotNull();
        assertThat(first.volumeInfo().title()).isEqualTo("Effective Java");
        assertThat(first.volumeInfo().authors()).isNotNull();
        assertThat(first.volumeInfo().language()).isNotNull();
        assertThat(first.searchInfo()).isNotNull();
        assertThat(first.searchInfo().textSnippet()).isNotNull();
    }

    /**
     * Executes a real external network call to lookup a specific valid ID (Spring in Action).
     */
    @Test
    void should_ReturnBook_When_FetchingByKnownId() {
        GoogleBook.Item item = googleBookService.getBookById("lRtdEAAAQBAJ");
        assertThat(item).isNotNull();
        assertThat(item.volumeInfo().title()).contains("Spring");
    }

    /**
     * Executes a real external network call using an invalid ID to ensure the live API's 
     * standard 404 response structure results in a HttpClientErrorException.NotFound.
     */
    @Test
    void should_ThrowRetrievalException_When_FetchingByFakeId() {
        assertThrows(
                org.springframework.web.client.HttpClientErrorException.NotFound.class, 
                () -> googleBookService.getBookById("this-id-is-completely-fake-12345")
        );
    }

    /**
     * Executes a real external search query using an empty string to ensure 
     * our system gracefully handles the lack of an 'items' array in the live response.
     */
    @Test
    void should_ReturnEmptyItems_When_SearchingEmptyString() {
        GoogleBook result = googleBookService.searchBooks("      ", 5, 0);
        assertThat(result.totalItems()).isZero();
        assertThat(result.items()).isNull();
    }
}
