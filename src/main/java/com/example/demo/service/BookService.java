package com.example.demo.service;

import com.example.demo.model.Book;
import com.example.demo.repository.BookRepository;
import com.example.demo.exception.GoogleBookRetrievalException;
import com.example.demo.client.google.GoogleBook;
import com.example.demo.client.google.GoogleBookService;
import com.example.demo.mapper.BookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Service class responsible for managing books within the application.
 * It orchestrates the process of fetching book data from external source,
 * validating input, and persisting entries to the local repository.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookService {

    private final BookRepository bookRepository;
    private final GoogleBookService googleBookService;
    private final BookMapper bookMapper;

    /**
     * Retrieves all books currently stored in the local repository.
     *
     * @return a list of all {@link Book} entities.
     */
    @Transactional(readOnly = true)
    public List<Book> getAllBooks() {
        log.debug("Fetching all books from repository");
        return bookRepository.findAll();
    }

    /**
     * Fetches a book from the Google Books API by its ID, maps it to a local
     * entity,
     * and saves it to the database.
     *
     * @param googleId the unique identifier from the Google Books API.
     * @return the saved {@link Book} entity.
     * @throws ResponseStatusException      if the ID is blank (400), if the book
     *                                      already exists (409),
     *                                      or if there is an upstream API error
     *                                      (500/upstream status).
     * @throws GoogleBookRetrievalException if the API returns invalid or incomplete
     *                                      book data.
     */
    @Transactional
    public Book addBookFromGoogle(String googleId) {
        if (googleId == null || googleId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google Book ID cannot be null or empty");
        }

        if (bookRepository.existsById(googleId)) {
            log.warn("Book with id={} already exists in local collection", googleId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Book already exists in your collection");
        }

        log.info("Fetching book from Google Books API with id={}", googleId);
        GoogleBook.Item item = googleBookService.getBookById(googleId);
        validateItem(googleId, item);
        Book book = bookMapper.toEntity(item);
        return saveBook(book);
    }


    /**
     * Validates that the data returned from the Google Books API is sufficient for
     * our entity and that the returned volume ID matches the requested ID.
     *
     * @param googleId the requested Google API volume ID.
     * @param item the API item to validate.
     * @throws GoogleBookRetrievalException if required data is missing or if the IDs mismatch.
     */
    private void validateItem(String googleId, GoogleBook.Item item) {
        if (item == null || item.volumeInfo() == null || item.volumeInfo().title() == null) {
            throw new GoogleBookRetrievalException("Invalid or missing book data from Google Books API");
        }
        
        if (!googleId.equals(item.id())) {
            log.warn("Mismatched volume ID returned from Google Books API. Requested: {}, Received: {}", googleId, item.id());
            throw new GoogleBookRetrievalException("Mismatched volume ID returned from Google Books API");
        }
    }

    /**
     * Persists the book entity to the local repository and logs the success.
     *
     * @param book the entity to save.
     * @return the saved entity with generated fields (if any).
     */
    private Book saveBook(Book book) {
        Book saved = bookRepository.save(book);
        log.info("Successfully persisted book id={}, title={}", saved.getId(), saved.getTitle());
        return saved;
    }
}
