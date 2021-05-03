package dev.sanda.datafi.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FreeTextSearchPageRequest extends PageRequest {

  private String searchTerm;
}
