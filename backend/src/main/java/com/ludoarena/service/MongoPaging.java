package com.ludoarena.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/** Small helpers so every list endpoint is paginated the same way (max 100 per page). */
public final class MongoPaging {
    private MongoPaging() {
    }

    public static Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
    }

    public static Pageable newestFirst(int page, int size) {
        return pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public static <T> Page<T> page(MongoTemplate mongo, Criteria criteria, Pageable pageable, Class<T> type) {
        Query query = criteria == null ? new Query() : new Query(criteria);
        long total = mongo.count(query, type);
        List<T> content = mongo.find(query.with(pageable), type);
        return new PageImpl<>(content, pageable, total);
    }
}
