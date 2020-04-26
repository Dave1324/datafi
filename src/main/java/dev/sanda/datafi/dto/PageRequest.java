package dev.sanda.datafi.dto;

import lombok.Data;
import org.springframework.data.domain.Sort;

import static org.springframework.data.domain.Sort.Direction.ASC;

@Data
public class PageRequest {
    private Integer pageNumber;
    private String sortBy;
    private Integer pageSize = 25;
    private Sort.Direction sortDirection = ASC;

    public boolean isValidPagingRange(){
        return pageNumber >= 0 && pageSize > 0;
    }
}
