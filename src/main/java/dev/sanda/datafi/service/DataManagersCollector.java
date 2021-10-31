package dev.sanda.datafi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DataManagersCollector {

  private final List<DataManager> dataManagers;

  private Map<Class, DataManager> dataManagersByEntityType;

  @PostConstruct
  private void init() {
    dataManagersByEntityType =
      dataManagers
        .stream()
        .filter(dataManager -> dataManager.getClazz() != null)
        .collect(Collectors.toMap(DataManager::getClazz, Function.identity()));
  }

  @SuppressWarnings("unchecked")
  public <T> DataManager<T> getDataManagerForEntityType(Class<T> clazz) {
    return (DataManager<T>) dataManagersByEntityType.get(clazz);
  }
}
