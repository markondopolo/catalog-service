package com.polarbookshop.catalogservice.domain;

import java.util.Optional;

public interface BookRepository {
    Iterable<Book> findAll();
    Optional<Book> findByIsbn(String isbn);
    boolean existsBookByIsbn(String isbn);
    Book save(Book book);
    void delete(String isbn);
}