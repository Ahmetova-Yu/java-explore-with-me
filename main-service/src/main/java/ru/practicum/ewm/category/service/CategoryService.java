package ru.practicum.ewm.category.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.dto.NewCategoryDto;
import ru.practicum.ewm.category.mapper.CategoryMapper;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.util.PaginationUtil;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public CategoryDto addCategory(NewCategoryDto dto) {
        log.debug("Добавление категории: name={}", dto.getName());
        Category category = categoryMapper.toEntity(dto);
        try {
            Category saved = categoryRepository.save(category);
            return categoryMapper.toDto(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Integrity constraint has been violated: category name must be unique");
        }
    }

    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto dto) {
        log.debug("Обновление категории: id={}, name={}", catId, dto.getName());
        Category category = getCategoryOrThrow(catId);
        if (dto.getName() != null && !dto.getName().isBlank()) {
            category.setName(dto.getName());
        }
        try {
            categoryRepository.flush();
            return categoryMapper.toDto(category);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Integrity constraint has been violated: category name must be unique");
        }
    }

    @Transactional
    public void deleteCategory(Long catId) {
        log.debug("Удаление категории: id={}", catId);
        Category category = getCategoryOrThrow(catId);
        if (categoryRepository.existsByCategoryId(catId)) {
            throw new ConflictException("The category is not empty");
        }
        categoryRepository.delete(category);
    }

    public List<CategoryDto> getCategories(Integer from, Integer size) {
        log.debug("Получение категорий: from={}, size={}", from, size);
        Pageable pageable = PaginationUtil.of(from, size);
        return categoryRepository.findAll(pageable).stream()
                .map(categoryMapper::toDto)
                .collect(Collectors.toList());
    }

    public CategoryDto getCategory(Long catId) {
        log.debug("Получение категории: id={}", catId);
        Category category = getCategoryOrThrow(catId);
        return categoryMapper.toDto(category);
    }

    private Category getCategoryOrThrow(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Category with id=%d was not found", catId)));
    }
}
