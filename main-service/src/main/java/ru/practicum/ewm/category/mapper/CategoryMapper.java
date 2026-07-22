package ru.practicum.ewm.category.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.dto.NewCategoryDto;
import ru.practicum.ewm.category.model.Category;

@Component
public class CategoryMapper {

    public CategoryDto toDto(Category category) {
        if (category == null) return null;
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .build();
    }

    public Category toEntity(NewCategoryDto dto) {
        if (dto == null) return null;
        return Category.builder()
                .name(dto.getName())
                .build();
    }
}
