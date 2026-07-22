package ru.practicum.ewm.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PaginationUtil {

    public static Pageable of(int from, int size) {
        if (size <= 0) {
            size = 10;
        }
        int page = from / Math.max(size, 1);
        return PageRequest.of(page, size);
    }

    public static Pageable of(int from, int size, Sort sort) {
        if (size <= 0) {
            size = 10;
        }
        int page = from / Math.max(size, 1);
        if (sort == null) {
            sort = Sort.by(Sort.Direction.ASC, "id");
        }
        return PageRequest.of(page, size, sort);
    }
}