package com.example.demo.client.google;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import com.example.demo.exception.GoogleBookRetrievalException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * Integration test using a mock server to simulate Google Books API.
 * Serves the JSON from src/test/resources/effectivejava.json.
 */
@SpringBootTest
class GoogleBookServiceIT {

    static MockWebServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        server.shutdown();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("google.books.base-url", () -> server.url("/").toString());
    }

    @Autowired
    private GoogleBookService googleBookService;

    /**
     * Verifies that searching for a keyword against the mocked Google API returns 
     * a fully deserialized GoogleBook object containing the expected items.
     */
    @Test
    void should_ReturnEffectiveJava_When_Searched() throws IOException {
        Path path = Paths.get("src", "test", "resources", "effectivejava.json");
        String body = Files.readString(path);
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body));
        GoogleBook result = googleBookService.searchBooks("effective+java", 5, 0);
        assertThat(result).isNotNull();
        assertThat(result.kind()).isEqualTo("books#volumes");
        assertThat(result.items()).isNotEmpty();
        GoogleBook.Item first = result.items().get(0);
        assertThat(first.volumeInfo().title()).isEqualTo("Effective Java");
    }

    /**
     * Verifies that retrieving a specific book ID successfully maps a single volume's
     * JSON response into the GoogleBook.Item record structure.
     */
    @Test
    void should_ReturnBook_When_GetBookByIdIsCalled() throws IOException {
        Path path = Paths.get("src", "test", "resources", "effectivejavaSingle.json");
        String body = Files.readString(path);
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body));

        GoogleBook.Item result = googleBookService.getBookById("ka2VUBqHiWkC");
        assertThat(result).isNotNull();
        assertThat(result.volumeInfo().title()).isEqualTo("Effective Java");
    }

    /**
     * Verifies that a 404 Not Found from the mocked Google API results in a 
     * HttpClientErrorException.NotFound being thrown by the RestClient.
     */
    @Test
    void should_ThrowRetrievalException_When_ApiReturns404() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThrows(
                GoogleBookRetrievalException.class, 
                () -> googleBookService.getBookById("invalid-id")
        );
    }

    /**
     * Verifies that upstream 500-level service errors result in a 
     * HttpServerErrorException.InternalServerError being thrown.
     */
    @Test
    void should_ThrowResponseStatusException_When_ApiReturns500() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThrows(
                ResponseStatusException.class, 
                () -> googleBookService.getBookById("crash-id")
        );
    }

    /**
     * Verifies that unexpected or malformed JSON payloads from the upstream API 
     * cause RestClientException (due to parsing failures).
     */
    @Test
    void should_HandleMalformedJson_When_ApiReturnsGarbage() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{ garbage data ]"));

        assertThrows(
                ResponseStatusException.class, 
                () -> googleBookService.getBookById("garbage-id")
        );
    }
}
