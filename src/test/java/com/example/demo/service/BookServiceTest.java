package com.example.demo.service;

import com.example.demo.model.Book;
import com.example.demo.repository.BookRepository;
import com.example.demo.exception.GoogleBookRetrievalException;
import com.example.demo.client.google.GoogleBook;
import com.example.demo.client.google.GoogleBookService;
import com.example.demo.mapper.BookMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

        @Mock
        private BookRepository bookRepository;

        @Mock
        private GoogleBookService googleBookService;

        @Mock
        private BookMapper bookMapper;

        @InjectMocks
        private BookService bookService;

        /**
         * Verifies that the service retrieves all books directly from the repository
         * without modification.
         */
        @Test
        void should_DelegateToRepository_When_GetAllBooksIsCalled() {
                List<Book> books = List.of(
                                new Book("1", "Spring in Action", "Craig Walls", null),
                                new Book("2", "Effective Java", "Joshua Bloch", 375));
                when(bookRepository.findAll()).thenReturn(books);

                List<Book> result = bookService.getAllBooks();

                assertThat(result).isEqualTo(books);
                verify(bookRepository, times(1)).findAll();
        }

        /**
         * Verifies the successful orchestration flow: fetching item from external
         * service,
         * mapping to entity, and persisting to repository.
         */
        @Test
        void should_MapAndPersistBook_When_ValidItemIsFetched() {
                GoogleBook.VolumeInfo volumeInfo = new GoogleBook.VolumeInfo(
                                "Effective Java", List.of("Joshua Bloch"),
                                "2008-05-08", "Addison-Wesley",
                                375, "BOOK", "NOT_MATURE",
                                List.of("Computers"), "en",
                                "http://preview", "http://info");
                GoogleBook.Item item = new GoogleBook.Item("ka2VUBqHiWkC", "http://selflink", volumeInfo, null);
                Book saved = new Book("ka2VUBqHiWkC", "Effective Java", "Joshua Bloch", 375);

                when(bookRepository.existsById("ka2VUBqHiWkC")).thenReturn(false);
                when(googleBookService.getBookById("ka2VUBqHiWkC")).thenReturn(item);
                when(bookMapper.toEntity(item)).thenReturn(saved);
                when(bookRepository.save(any(Book.class))).thenReturn(saved);

                Book result = bookService.addBookFromGoogle("ka2VUBqHiWkC");

                assertThat(result.getId()).isEqualTo("ka2VUBqHiWkC");
                assertThat(result.getTitle()).isEqualTo("Effective Java");
                assertThat(result.getAuthor()).isEqualTo("Joshua Bloch");
                assertThat(result.getPageCount()).isEqualTo(375);
                verify(bookMapper).toEntity(item);
                verify(bookRepository, times(1)).save(any(Book.class));
        }

        /**
         * Verifies that Google Books items without authors are gracefully handled and
         * mapped with a null author, rather than throwing NullPointerExceptions.
         */
        @Test
        void should_UseNullAuthor_When_NoAuthorsAreReturned() {
                GoogleBook.VolumeInfo volumeInfo = new GoogleBook.VolumeInfo(
                                "Unknown Title", null,
                                null, null, null,
                                null, null, null, null, null, null);
                GoogleBook.Item item = new GoogleBook.Item("someId", "http://selflink", volumeInfo, null);
                Book saved = new Book("someId", "Unknown Title", null, null);

                when(bookRepository.existsById("someId")).thenReturn(false);
                when(googleBookService.getBookById("someId")).thenReturn(item);
                when(bookMapper.toEntity(item)).thenReturn(saved);
                when(bookRepository.save(any(Book.class))).thenReturn(saved);

                Book result = bookService.addBookFromGoogle("someId");

                assertThat(result.getAuthor()).isNull();
                verify(bookMapper).toEntity(item);
                verify(bookRepository).save(any(Book.class));
        }

        /**
         * Verifies that validation or mapping exceptions thrown by the BookMapper
         * bubble up cleanly without persisting any partial state to the DB.
         */
        @Test
        void should_PropagateException_When_MapperFails() {
                GoogleBook.VolumeInfo volumeInfo = new GoogleBook.VolumeInfo(
                                "Valid Title", null, null, null, null, null, null, null, null, null, null);
                GoogleBook.Item item = new GoogleBook.Item("id", "http://selflink", volumeInfo, null);
                when(bookRepository.existsById("id")).thenReturn(false);
                when(googleBookService.getBookById("id")).thenReturn(item);
                when(bookMapper.toEntity(item)).thenThrow(new GoogleBookRetrievalException("Invalid data"));

                assertThrows(
                                GoogleBookRetrievalException.class,
                                () -> bookService.addBookFromGoogle("id"));

                verify(bookRepository, never()).save(any(Book.class));
        }

        /**
         * Verifies that a 404 from the external API
         * is transformed correctly into the domain's
         * GoogleBookRetrievalException.
         */
        @Test
        void should_ThrowRetrievalException_When_BookIsNotFound() {
                when(bookRepository.existsById("invalid-404")).thenReturn(false);
                when(googleBookService.getBookById("invalid-404"))
                                .thenThrow(new GoogleBookRetrievalException("Book not found in Google Books API"));

                GoogleBookRetrievalException ex = assertThrows(
                                GoogleBookRetrievalException.class,
                                () -> bookService.addBookFromGoogle("invalid-404"));

                assertThat(ex.getMessage()).isEqualTo("Book not found in Google Books API");
                verify(bookRepository, never()).save(any(Book.class));
        }

        /**
         * Verifies that catastrophic generic runtime exceptions encountered during the
         * API call are converted uniformly to 500 Internal Server Error
         * ResponseStatusExceptions.
         */
        @Test
        void should_ThrowInternalServerError_When_UpstreamFails() {
                when(bookRepository.existsById("invalid")).thenReturn(false);
                when(googleBookService.getBookById("invalid"))
                                .thenThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error fetching book from Google Books API"));

                ResponseStatusException ex = assertThrows(
                                ResponseStatusException.class,
                                () -> bookService.addBookFromGoogle("invalid"));

                assertThat(ex.getStatusCode().value()).isEqualTo(500);
                verify(bookRepository, never()).save(any());
        }

        /**
         * Verifies that HTTP status errors from the upstream API are correctly
         * captured and their HTTP statuses are propagated cleanly.
         */
        @Test
        void should_PropagateStatus_When_UpstreamRestClientExceptionOccurs() {
                when(bookRepository.existsById("invalid-502")).thenReturn(false);
                when(googleBookService.getBookById("invalid-502"))
                                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch book from Google Books API"));

                ResponseStatusException ex = assertThrows(
                                ResponseStatusException.class,
                                () -> bookService.addBookFromGoogle("invalid-502"));

                assertThat(ex.getStatusCode().value()).isEqualTo(502);
                verify(bookRepository, never()).save(any());
        }

        @Test
        void should_ThrowBadRequest_When_GoogleIdIsNull() {
                ResponseStatusException ex = assertThrows(
                                ResponseStatusException.class,
                                () -> bookService.addBookFromGoogle(null));

                assertThat(ex.getStatusCode().value()).isEqualTo(400);
                verify(bookRepository, never()).existsById(any());
                verify(googleBookService, never()).getBookById(any());
        }

        @Test
        void should_ThrowBadRequest_When_GoogleIdIsEmpty() {
                ResponseStatusException ex = assertThrows(
                                ResponseStatusException.class,
                                () -> bookService.addBookFromGoogle(""));

                assertThat(ex.getStatusCode().value()).isEqualTo(400);
                verify(bookRepository, never()).existsById(any());
                verify(googleBookService, never()).getBookById(any());
        }

        @Test
        void should_ThrowBadRequest_When_GoogleIdIsBlank() {
                ResponseStatusException ex = assertThrows(
                                ResponseStatusException.class,
                                () -> bookService.addBookFromGoogle("  "));

                assertThat(ex.getStatusCode().value()).isEqualTo(400);
                verify(bookRepository, never()).existsById(any());
                verify(googleBookService, never()).getBookById(any());
        }

        /**
         * Verifies that if a book ID already exists in the repository,
         * the service short-circuits and throws a 409 Conflict without calling the external API.
         */
        @Test
        void should_ThrowConflict_When_BookAlreadyExists() {
                when(bookRepository.existsById("existing-id")).thenReturn(true);

                ResponseStatusException ex = assertThrows(
                                ResponseStatusException.class,
                                () -> bookService.addBookFromGoogle("existing-id"));

                assertThat(ex.getStatusCode().value()).isEqualTo(409);
                verify(googleBookService, never()).getBookById(any());
                verify(bookRepository, never()).save(any());
        }

        /**
         * Verifies that if the external API returns an item with an ID different
         * from the one requested, the validation fails and throws an exception.
         */
        @Test
        void should_ThrowRetrievalException_When_IdMismatch() {
                GoogleBook.VolumeInfo volumeInfo = new GoogleBook.VolumeInfo(
                                "Mismatched Title", List.of("Author"), null, null, null, null, null, null, null, null, null);
                GoogleBook.Item item = new GoogleBook.Item("different-id", "http://selflink", volumeInfo, null);
                
                when(bookRepository.existsById("requested-id")).thenReturn(false);
                when(googleBookService.getBookById("requested-id")).thenReturn(item);

                GoogleBookRetrievalException ex = assertThrows(
                                GoogleBookRetrievalException.class,
                                () -> bookService.addBookFromGoogle("requested-id"));

                assertThat(ex.getMessage()).isEqualTo("Mismatched volume ID returned from Google Books API");
                verify(bookMapper, never()).toEntity(any());
                verify(bookRepository, never()).save(any());
        }
}
