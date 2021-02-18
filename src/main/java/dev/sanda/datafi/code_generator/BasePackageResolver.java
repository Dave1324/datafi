package dev.sanda.datafi.code_generator;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class BasePackageResolver {
    private List<String> basePackages;
}
