package com.example.demo.controller;

import com.example.demo.model.Book;
import com.example.demo.client.google.GoogleBook;
import com.example.demo.client.google.GoogleBookService;
import com.example.demo.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final GoogleBookService googleBookService;

    @GetMapping("/books")
    public List<Book> getAllBooks() {
        return bookService.getAllBooks();
    }

    @PostMapping("/books/{googleId}")
    public ResponseEntity<Book> addBookFromGoogle(@PathVariable String googleId) {
        Book saved = bookService.addBookFromGoogle(googleId);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/google")
    public GoogleBook searchGoogleBooks(@RequestParam("q") String query,
            @RequestParam(value = "maxResults", required = false) Integer maxResults,
            @RequestParam(value = "startIndex", required = false) Integer startIndex) {
        return googleBookService.searchBooks(query, maxResults, startIndex);
    }

    @GetMapping("/google/{id}")
    public GoogleBook.Item getGoogleBookDetails(@PathVariable String id) {
        return googleBookService.getBookById(id);
    }
}
