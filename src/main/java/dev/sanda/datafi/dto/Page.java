package dev.sanda.datafi.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter
@NoArgsConstructor
public class Page<T> {
    private List<T> content;
    private Long totalPagesCount;
    private Long totalItemsCount;

    public Page(org.springframework.data.domain.Page<T> page){
        content = page.getContent();
        totalPagesCount = (long) page.getTotalPages();
        totalItemsCount = page.getTotalElements();
    }
}
