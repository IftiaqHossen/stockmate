package com.stockmate.stockmate.dto.response;


/** CategoryResponse — returned by CategoryService. */
public record CategoryResponse(
        Long   id,
        String name,
        String description
) {}