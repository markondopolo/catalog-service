package com.polarbookshop.catalogservice.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    private BookService bookService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bookService = new BookService(bookRepository);

    }

    private Book sampleBook(Long id, String isbn, String title, String author, Double price) {
        return new Book(
                id,
                isbn,
                title,
                author,
                price,
                null,
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-01-01T00:00:00Z"),
                0
        );
    }

    @Test
    void viewBookList_delegatesToRepository() {
        var b1 = sampleBook(1L, "1111111111", "T1", "A1", 5.0);
        var b2 = sampleBook(2L, "2222222222", "T2", "A2", 6.0);

        when(bookRepository.findAll()).thenReturn(java.util.List.of(b1, b2));

        var result = bookService.viewBookList();

        assertThat(result).containsExactlyInAnyOrder(b1, b2);
        verify(bookRepository).findAll();
    }

    @Test
    void viewBookDetails_found_returnsBook() {
        var book = sampleBook(1L, "1234567890", "Title", "Author", 9.9);
        when(bookRepository.findByIsbn("1234567890")).thenReturn(Optional.of(book));

        var result = bookService.viewBookDetails("1234567890");

        assertThat(result).isEqualTo(book);
        verify(bookRepository).findByIsbn("1234567890");
    }

    @Test
    void viewBookDetails_notFound_throwsBookNotFoundException() {
        String fakeIsbn = "missing";

        when(bookRepository.findByIsbn(fakeIsbn)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.viewBookDetails(fakeIsbn))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining(fakeIsbn);

        verify(bookRepository).findByIsbn(fakeIsbn);
    }


    @Test
    void addBookToCatalog_success_callsSaveAndReturns() {
        var book = sampleBook(null, "9876543210", "New", "Auth", 7.5);

        when(bookRepository.existsBookByIsbn(book.isbn())).thenReturn(false);
        when(bookRepository.save(book)).thenReturn(
                sampleBook(10L, book.isbn(), book.title(), book.author(), book.price())
        );

        var result = bookService.addBookToCatalog(book);

        assertThat(result).isNotNull();
        assertThat(result.isbn()).isEqualTo(book.isbn());
        verify(bookRepository).existsBookByIsbn(book.isbn());
        verify(bookRepository).save(book);
    }

    @Test
    void addBookToCatalog_alreadyExists_throwsAndDoesNotSave() {
        var book = sampleBook(null, "5555555555", "Dup", "Author", 4.0);

        when(bookRepository.existsBookByIsbn(book.isbn())).thenReturn(true);

        assertThatThrownBy(() -> bookService.addBookToCatalog(book))
                .isInstanceOf(BookAlreadyExistsException.class)
                .hasMessageContaining(book.isbn());

        verify(bookRepository).existsBookByIsbn(book.isbn());
        verify(bookRepository, never()).save(any());
    }

    @Test
    void removeBookFromCatalog_delegatesDelete() {
        bookService.removeBookFromCatalog("to-delete");
        verify(bookRepository).deleteByIsbn("to-delete");
    }

    @Test
    void editBookDetails_whenExisting_updatesAndSaves() {
        var existing = sampleBook(1L, "1111111111", "OldTitle", "OldAuthor", 5.0);
        var update = sampleBook(null, "1111111111", "NewTitle", "NewAuthor", 8.0);

        // repository.findByIsbn should return the existing book
        when(bookRepository.findByIsbn("1111111111")).thenReturn(Optional.of(existing));

        // Expectation: repository.save will be called with a Book that preserves id and isbn but uses updated fields
        var savedResult = sampleBook(existing.id(), existing.isbn(), update.title(), update.author(), update.price());
        when(bookRepository.save(any(Book.class))).thenReturn(savedResult);

        var result = bookService.editBookDetails("1111111111", update);

        assertThat(result).isEqualTo(savedResult);

        // capture argument passed to save to assert fields
        var captor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(captor.capture());
        var passed = captor.getValue();

        assertThat(passed.id()).isEqualTo(existing.id());
        assertThat(passed.isbn()).isEqualTo(existing.isbn());
        assertThat(passed.title()).isEqualTo(update.title());
        assertThat(passed.author()).isEqualTo(update.author());
        assertThat(passed.price()).isEqualTo(update.price());
    }

    @Test
    void editBookDetails_whenNotExisting_createsNewBook() {
        var incoming = sampleBook(null, "7777777777", "TitleX", "AuthorX", 3.3);

        when(bookRepository.findByIsbn(incoming.isbn())).thenReturn(Optional.empty());
        when(bookRepository.existsBookByIsbn(incoming.isbn())).thenReturn(false);
        when(bookRepository.save(incoming)).thenReturn(sampleBook(5L, incoming.isbn(), incoming.title(), incoming.author(), incoming.price()));

        var result = bookService.editBookDetails(incoming.isbn(), incoming);

        // If not found, editBookDetails should delegate to addBookToCatalog -> which calls repository.save
        verify(bookRepository).save(incoming);
        assertThat(result.isbn()).isEqualTo(incoming.isbn());
    }
}
