package com.example.demo.controller;

import com.example.demo.model.Book;
import com.example.demo.repository.BookRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookControllerIT {

    static MockWebServer mockServer;

    @BeforeAll
    static void startServer() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockServer.shutdown();
    }

    @DynamicPropertySource
    static void configureGoogleBooksBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("google.books.base-url", () -> mockServer.url("/").toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void setup() {
        bookRepository.deleteAll();
        bookRepository.save(new Book("lRtdEAAAQBAJ", "Spring in Action", "Craig Walls"));
        bookRepository.save(new Book("12muzgEACAAJ", "Effective Java", "Joshua Bloch"));
    }

    /**
     * Verifies that GET /books returns a 200 OK and a JSON array containing all
     * seeded books.
     */
    @Test
    void should_ReturnAllBooks_When_Requested() throws Exception {
        mockMvc.perform(get("/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Spring in Action"))
                .andExpect(jsonPath("$[1].title").value("Effective Java"));
    }

    /**
     * Verifies that GET /google?q={query} calls the upstream API and returns
     * search results.
     */
    @Test
    void should_ReturnSearchResults_When_GoogleApiIsCalled() throws Exception {
        String json = Files.readString(Paths.get("src", "test", "resources", "effectivejava.json"));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(json));

        mockMvc.perform(get("/google").param("q", "Effective Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].volumeInfo.title").value("Effective Java"));
    }

    /**
     * Verifies that GET /google/{id} calls the upstream API and returns book
     * details.
     */
    @Test
    void should_ReturnVolumeDetails_When_GoogleApiIsCalled() throws Exception {
        String json = Files.readString(Paths.get("src", "test", "resources", "effectivejavaSingle.json"));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(json));

        mockMvc.perform(get("/google/ka2VUBqHiWkC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volumeInfo.title").value("Effective Java"));
    }

    /**
     * Verifies that POST /books/{googleId} creates a new book by communicating with
     * the external Mock API.
     * Asserts a 201 Created response and verifies fields are correctly persisted to
     * the database.
     */
    @Test
    void should_ReturnCreatedAndPersistBook_When_GoogleIdIsValid() throws Exception {
        String json = Files.readString(Paths.get("src", "test", "resources", "effectivejavaSingle.json"));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(json));

        mockMvc.perform(post("/books/ka2VUBqHiWkC"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ka2VUBqHiWkC"))
                .andExpect(jsonPath("$.title").value("Effective Java"))
                .andExpect(jsonPath("$.author").value("Joshua Bloch"))
                .andExpect(jsonPath("$.pageCount").value(375));

        // assert persisted in DB with correct fields
        Book persisted = bookRepository.findById("ka2VUBqHiWkC").orElseThrow();
        assertThat(persisted.getId()).isEqualTo("ka2VUBqHiWkC");
        assertThat(persisted.getTitle()).isEqualTo("Effective Java");
        assertThat(persisted.getAuthor()).isEqualTo("Joshua Bloch");
        assertThat(persisted.getPageCount()).isEqualTo(375);
    }

    /**
     * Verifies that a 404 from the upstream Google Books API translates clean to a
     * 400 Bad Request
     * and ensures no malformed records are persisted to the database.
     */
    @Test
    void should_ReturnBadRequest_When_UpstreamReturnsError() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(404));

        mockMvc.perform(post("/books/invalid-google-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Book not found in Google Books API"))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(bookRepository.count()).isEqualTo(2);
    }

    /**
     * Verifies duplicate entry prevention. If a book ID already exists in the
     * database,
     * the controller should short-circuit the API call and return 409 Conflict.
     */
    @Test
    void should_ReturnConflict_When_BookAlreadyExists() throws Exception {
        int initialRequestCount = mockServer.getRequestCount();
        
        mockMvc.perform(post("/books/lRtdEAAAQBAJ"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Book already exists in your collection"))
                .andExpect(jsonPath("$.timestamp").exists());

        // Verify that NO API call was made during this test
        assertThat(mockServer.getRequestCount()).isEqualTo(initialRequestCount);
    }

    /**
     * Verifies input validation. An invalid or blank URL path variable should
     * result in a 400 Bad Request,
     * without attempting any upstream server communication.
     */
    @Test
    void should_ReturnBadRequest_When_GoogleIdIsBlank() throws Exception {
        int initialRequestCount = mockServer.getRequestCount();

        mockMvc.perform(post("/books/ "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Google Book ID cannot be null or empty"))
                .andExpect(jsonPath("$.timestamp").exists());

        // Verify that NO API call was made during this test
        assertThat(mockServer.getRequestCount()).isEqualTo(initialRequestCount);
    }
}
