package dev.sanda.datafi.dto;

import static org.springframework.data.domain.Sort.Direction.ASC;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.data.domain.Sort;

@Data
public class PageRequest {

  private Integer pageNumber;
  private String sortBy;
  private Integer pageSize = 25;
  private Sort.Direction sortDirection = ASC;
  private Boolean fetchAll = false;

  private Map<String, Object> customArgs = new HashMap<>();

  public boolean isValidPagingRange() {
    return pageNumber >= 0 && pageSize > 0;
  }

  @SuppressWarnings("unchecked")
  public <T> T getCustomArg(String key) {
    return customArgs.containsKey(key) ? (T) customArgs.get(key) : null;
  }
}
