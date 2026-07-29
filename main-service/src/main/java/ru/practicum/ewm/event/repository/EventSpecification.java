package ru.practicum.ewm.event.repository;

import org.springframework.data.jpa.domain.Specification;
import ru.practicum.ewm.event.enums.State;
import ru.practicum.ewm.event.model.Event;

import java.time.LocalDateTime;
import java.util.List;

public class EventSpecification {

    public static Specification<Event> hasUsers(List<Long> users) {
        return (root, query, cb) -> users == null || users.isEmpty()
                ? cb.conjunction()
                : root.get("initiator").get("id").in(users);
    }

    public static Specification<Event> hasStates(List<State> states) {
        return (root, query, cb) -> states == null || states.isEmpty()
                ? cb.conjunction()
                : root.get("state").in(states);
    }

    public static Specification<Event> hasCategories(List<Long> categories) {
        return (root, query, cb) -> categories == null || categories.isEmpty()
                ? cb.conjunction()
                : root.get("category").get("id").in(categories);
    }

    public static Specification<Event> hasRangeStart(LocalDateTime rangeStart) {
        return (root, query, cb) -> rangeStart == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("eventDate"), rangeStart);
    }

    public static Specification<Event> hasRangeEnd(LocalDateTime rangeEnd) {
        return (root, query, cb) -> rangeEnd == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("eventDate"), rangeEnd);
    }


    public static Specification<Event> isPublished() {
        return (root, query, cb) -> cb.equal(root.get("state"), State.PUBLISHED);
    }

    public static Specification<Event> hasText(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) return cb.conjunction();
            String pattern = "%" + text.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("annotation")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }

    public static Specification<Event> isPaid(Boolean paid) {
        return (root, query, cb) -> paid == null
                ? cb.conjunction()
                : cb.equal(root.get("paid"), paid);
    }

    public static Specification<Event> isOnlyAvailable(Boolean onlyAvailable) {
        return (root, query, cb) -> {
            if (!Boolean.TRUE.equals(onlyAvailable)) return cb.conjunction();
            return cb.or(
                    cb.equal(root.get("participantLimit"), 0),
                    cb.greaterThan(root.get("participantLimit"), root.get("confirmedRequests"))
            );
        };
    }

    public static Specification<Event> forAdmin(
            List<Long> users,
            List<State> states,
            List<Long> categories,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd) {
        return Specification.where(hasUsers(users))
                .and(hasStates(states))
                .and(hasCategories(categories))
                .and(hasRangeStart(rangeStart))
                .and(hasRangeEnd(rangeEnd));
    }

    public static Specification<Event> forPublic(
            String text,
            List<Long> categories,
            Boolean paid,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            Boolean onlyAvailable) {
        return Specification.where(isPublished())
                .and(hasText(text))
                .and(hasCategories(categories))
                .and(isPaid(paid))
                .and(hasRangeStart(rangeStart))
                .and(hasRangeEnd(rangeEnd))
                .and(isOnlyAvailable(onlyAvailable));
    }
}
