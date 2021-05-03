package dev.sanda.datafi.code_generator;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BasePackageResolver {

  private List<String> basePackages;
}
