package dev.sanda.datafi.code_generator;

import lombok.Data;
import lombok.NonNull;

@Data
public class BasePackageResolver {
    @NonNull
    private String basePackage;
}
