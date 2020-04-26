package dev.sanda.datafi.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class Page<T> {
    private final List<T> content;
    private final long totalPagesCount;
    private final long totalRecordsCount;

    public Page(org.springframework.data.domain.Page<T> page){
        content = page.getContent();
        totalPagesCount = page.getTotalPages();
        totalRecordsCount = page.getTotalElements();
    }
}
