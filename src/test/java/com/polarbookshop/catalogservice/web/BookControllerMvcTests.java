package com.polarbookshop.catalogservice.web;

import com.polarbookshop.catalogservice.domain.Book;
import com.polarbookshop.catalogservice.domain.BookAlreadyExistsException;
import com.polarbookshop.catalogservice.domain.BookNotFoundException;
import com.polarbookshop.catalogservice.domain.BookService;
import com.polarbookshop.catalogservice.domain.web.BookController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookController.class)
public class BookControllerMvcTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @Autowired
    private ObjectMapper objectMapper;


    private Book sampleBook(String isbn) {
        return new Book(
                1L,
                isbn,
                "Sample Title",
                "Sample Author",
                9.90,
                null,
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-01-01T00:00:00Z"),
                0
        );
    }

    private String bookJson(String isbn, String title, String author, double price)
            throws Exception {
        var map = new java.util.HashMap<String, Object>();
        map.put("isbn", isbn);
        map.put("title", title);
        map.put("author", author);
        map.put("price", price);
        return objectMapper.writeValueAsString(map);
    }

    @Test
    void whenGetBookNotExistingThenShouldReturn404() throws Exception {
        String isbn = "73737313940";
        given(bookService.viewBookDetails(isbn))
                .willThrow(BookNotFoundException.class);
        mockMvc
                .perform(get("/books/" + isbn))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAll_whenBooksExist_returns200AndJsonArray() throws Exception {
        var b1 = sampleBook("1111111111");
        var b2 = sampleBook("2222222222");
        given(bookService.viewBookList()).willReturn(List.of(b1, b2));

        mockMvc.perform(get("/books"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].isbn").value("1111111111"))
                .andExpect(jsonPath("$[1].isbn").value("2222222222"));
    }

    @Test
    void getByIsbn_whenExists_returns200AndBody() throws Exception {
        var book = sampleBook("1234567890");
        given(bookService.viewBookDetails("1234567890")).willReturn(book);

        mockMvc.perform(get("/books/1234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value("1234567890"))
                .andExpect(jsonPath("$.title").value("Sample Title"));
    }

    @Test
    void post_whenValid_returns201AndBody() throws Exception {
        String isbn = "9999999999";
        var saved = sampleBook(isbn);
        given(bookService.addBookToCatalog(any(Book.class))).willReturn(saved);

        var json = bookJson(isbn, "New Book", "Author", 15.5);

        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value(isbn));
    }

    @Test
    void post_whenValidationFails_returns400WithFieldErrors() throws Exception {
        // invalid: empty title, bad isbn, negative price
        var invalidJson = "{\"isbn\":\"bad\",\"title\":\"\",\"author\":\"\",\"price\":-1}";

        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isbn").exists())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void post_whenDuplicate_returns422() throws Exception {
        String isbn = "5555555555";
        var json = bookJson(isbn, "Dup", "Auth", 7.0);

        given(bookService.addBookToCatalog(any(Book.class)))
                .willThrow(new BookAlreadyExistsException(isbn));

        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(isbn)));
    }

    @Test
    void put_whenUpdateExisting_returnsUpdatedBook() throws Exception {
        String isbn = "1111111111";

        var updated = new Book(
                1L,
                isbn,
                "Updated Title",
                "Updated Author",
                11.0,
                null,
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-01-01T00:00:00Z"),
                0
        );
        given(bookService.editBookDetails(eq(isbn), any(Book.class))).willReturn(updated);

        var json = bookJson(isbn, "Updated Title", "Updated Author", 11.0);

        mockMvc.perform(put("/books/" + isbn)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value(isbn))
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.author").value("Updated Author"))
                .andExpect(jsonPath("$.price").value(11.0));
    }

    @Test
    void put_whenCreatesNew_currentController_returns200_butSpecMayWant201() throws Exception {
        String isbn = "7777777777";
        var created = sampleBook(isbn);
        given(bookService.editBookDetails(eq(isbn), any(Book.class))).willReturn(created);

        var json = bookJson(isbn, "Created Title", "Author", 5.0);

        mockMvc.perform(put("/books/" + isbn)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk()) // change to isCreated() if you change controller
                .andExpect(jsonPath("$.isbn").value(isbn));
    }

    @Test
    void delete_whenExists_returns204() throws Exception {
        willDoNothing().given(bookService).removeBookFromCatalog("to-delete");

        mockMvc.perform(delete("/books/to-delete"))
                .andExpect(status().isNoContent());
    }
}