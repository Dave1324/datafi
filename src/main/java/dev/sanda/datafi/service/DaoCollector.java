package dev.sanda.datafi.service;

import dev.sanda.datafi.persistence.GenericDao;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class DaoCollector {
    @Autowired
    @Getter
    private List<? extends GenericDao> daos;
}
