package dev.sanda.datafi.reflection.cached_type_info;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Field;

@lombok.Getter
@lombok.Setter
@RequiredArgsConstructor
public class CachedEntityField {
    @NonNull
    private Field field;
    @NonNull
    private boolean isCollectionOrMap;
    @NonNull
    private boolean isNonApiUpdatable;
    @NonNull
    private boolean isNonNullable;
}
