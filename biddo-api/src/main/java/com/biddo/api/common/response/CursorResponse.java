package com.biddo.api.common.response;

import lombok.Getter;

import java.util.List;

@Getter
public class CursorResponse<T> {

    private final List<T> content;
    private final String nextCursor;
    private final boolean hasNext;

    private CursorResponse(List<T> content, String nextCursor, boolean hasNext) {
        this.content = content;
        this.nextCursor = nextCursor;
        this.hasNext = hasNext;
    }

    public static <T> CursorResponse<T> of(List<T> content, String nextCursor, boolean hasNext) {
        return new CursorResponse<>(content, nextCursor, hasNext);
    }
}