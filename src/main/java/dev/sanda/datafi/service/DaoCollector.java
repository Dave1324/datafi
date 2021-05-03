package dev.sanda.datafi.service;

import dev.sanda.datafi.persistence.GenericDao;
import java.util.List;

public interface DaoCollector {
  List<? extends GenericDao> getDaos();
}
