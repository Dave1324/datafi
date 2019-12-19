package dev.sanda.datafi.service;

import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * can be autowired into a service layer bean for
 * complete coverage of jpa repository operations
 * @param <T>
 */
@NoArgsConstructor
public class DataManager<T> extends BaseDataManager<T> {
    public DataManager(@NonNull Class<T> clazz) {
        super(clazz);
    }
}
