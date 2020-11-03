package dev.sanda.datafi.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter @Setter
@NoArgsConstructor
public class Page<T> {
    private List<T> content;
    private Long totalPagesCount;
    private Long totalItemsCount;
    private Integer pageNumber;

    private Map<String, Object> customValues = new HashMap<>();

    public Page(org.springframework.data.domain.Page<T> page){
        content = page.getContent();
        totalPagesCount = (long) page.getTotalPages();
        totalItemsCount = page.getTotalElements();
        pageNumber = page.getNumber();
    }
}
