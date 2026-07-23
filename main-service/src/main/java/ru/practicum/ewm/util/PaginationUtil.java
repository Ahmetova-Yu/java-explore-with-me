package ru.practicum.ewm.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PaginationUtil {

    public static Pageable of(int from, int size) {
        int page = from / Math.max(size, 1);
        return PageRequest.of(page, size);
    }

    public static Pageable of(int from, int size, Sort sort) {
        int page = from / Math.max(size, 1);
        return PageRequest.of(page, size, sort);
    }
}
