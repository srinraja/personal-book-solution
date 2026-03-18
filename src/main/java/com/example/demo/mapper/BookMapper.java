package com.example.demo.mapper;

import com.example.demo.model.Book;
import com.example.demo.client.google.GoogleBook;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * MapStruct mapper responsible for converting Google Books API response objects
 * to the application's {@link Book} domain entity.
 */
@Mapper(componentModel = "spring")
public interface BookMapper {

    @Mapping(target = "author", source = "volumeInfo.authors", qualifiedByName = "mapAuthors")
    @Mapping(target = "title", source = "volumeInfo.title")
    @Mapping(target = "pageCount", source = "volumeInfo.pageCount")
    Book toEntity(GoogleBook.Item item);

    @Named("mapAuthors")
    default String mapAuthors(List<String> authors) {
        if (authors == null || authors.isEmpty()) {
            return null;
        }
        return String.join(", ", authors);
    }
}
