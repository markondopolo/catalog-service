package com.polarbookshop.catalogservice;


import com.polarbookshop.catalogservice.domain.Book;
import com.polarbookshop.catalogservice.domain.BookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.jdbc.core.JdbcTemplate;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("integration")
public class CatalogServiceIntegrationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("catalog_test")
            .withUsername("user")
            .withPassword("password");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void fullCrudFlow_postGetPutDelete() {
        String isbn = "1234567890123";
        var book = Book.of(isbn, "Title", "Author", 10.0, null);

        // POST
        var created = webTestClient.post().uri("/books")
                .bodyValue(book)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Book.class)
                .returnResult()
                .getResponseBody();
        assertThat(created).isNotNull();
        assertThat(created.isbn()).isEqualTo(isbn);

        // GET
        webTestClient.get().uri("/books/" + isbn)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Book.class)
                .value(b -> assertThat(b.title()).isEqualTo("Title"));

        // PUT (update)
        var updated = Book.of(isbn, "Updated Title", "Author", 12.0, null);
        webTestClient.put().uri("/books/" + isbn)
                .bodyValue(updated)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Book.class)
                .value(b -> assertThat(b.title()).isEqualTo("Updated Title"));

        // DELETE
        webTestClient.delete().uri("/books/" + isbn)
                .exchange()
                .expectStatus().isNoContent();

        // GET after delete
        webTestClient.get().uri("/books/" + isbn)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void postValidation_enforced_badRequestContainsErrors() {
        webTestClient.post().uri("/books")
                .bodyValue(Map.of("isbn", "bad", "title", "", "author", "", "price", -1))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.isbn").exists()
                .jsonPath("$.title").exists()
                .jsonPath("$.author").exists()
                .jsonPath("$.price").exists();
    }

    @Test
    void postDuplicate_returns422_serviceLevel() {
        String isbn = "1234567890124";
        var book = Book.of(isbn, "TitleDup", "Author", 10.0, null);

        // First insert
        webTestClient.post().uri("/books")
                .bodyValue(book)
                .exchange()
                .expectStatus().isCreated();

        // Duplicate insert
        webTestClient.post().uri("/books")
                .bodyValue(book)
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    @Test
    void insertingDuplicateIsbnShouldFailDueToUniqueConstraint() {
        var isbn = "1234567890125";
        var book1 = Book.of(isbn, "First", "Author", 10.0, null);
        var book2 = Book.of(isbn, "Duplicate", "Author", 12.0, null);
        jdbcAggregateTemplate.insert(book1);

        assertThatThrownBy(() -> jdbcAggregateTemplate.insert(book2))
                .isInstanceOf(DbActionExecutionException.class)
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    @Test
    void auditing_createdAndLastModifiedSet() throws InterruptedException {
        String isbn = "1234567890126";
        var book = Book.of(isbn, "AuditBook", "Author", 10.0, null);

        // insert
        jdbcAggregateTemplate.insert(book);

        // fetch inserted (contains id, createdDate, lastModifiedDate, version)
        var fetched = bookRepository.findByIsbn(isbn).orElseThrow();
        assertThat(fetched.createdDate()).isNotNull();
        assertThat(fetched.lastModifiedDate()).isNotNull();

        Instant originalLastModified = fetched.lastModifiedDate();

        // wait to ensure lastModified changes
        Thread.sleep(1000);

        // build updated book preserving id and version
        var bookToUpdate = new Book(
                fetched.id(),
                fetched.isbn(),
                "AuditBookUpdated",
                fetched.author(),
                12.0,
                fetched.publisher(),
                fetched.createdDate(),
                fetched.lastModifiedDate(),
                fetched.version()
        );

        // update — SD JDBC проверит version и обновит lastModified/version
        jdbcAggregateTemplate.update(bookToUpdate);
        // либо: bookRepository.save(bookToUpdate);

        var fetchedUpdated = bookRepository.findByIsbn(isbn).orElseThrow();
        assertThat(fetchedUpdated.lastModifiedDate()).isAfter(originalLastModified);
    }

    @Test
    void putCreates_whenNotExisting_returns200() {
        String isbn = "1234567890127";

        var book = Book.of(isbn, "NewPutBook", "Author", 10.0, null);

        webTestClient.put().uri("/books/" + isbn)
                .bodyValue(book)
                .exchange()
                .expectStatus().isOk() // current controller returns 200
                .expectBody(Book.class)
                .value(b -> assertThat(b.isbn()).isEqualTo(isbn));
    }

    @Test
    void flyway_migrations_applied_on_startup() {
        Integer tableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'book'",
                Integer.class
        );

        assertThat(tableExists).isGreaterThan(0);
    }
}
