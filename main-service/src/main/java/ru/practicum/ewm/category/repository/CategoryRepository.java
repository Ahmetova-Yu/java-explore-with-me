package ru.practicum.ewm.category.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.category.model.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("SELECT EXISTS (SELECT 1 FROM Event e WHERE e.category.id = :categoryId)")
    boolean existsByCategoryId(@Param("categoryId") Long categoryId);
}
