package com.example.concurrency.repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import org.springframework.stereotype.Repository;

@Repository
public class NamedLockRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void getLock(Long lockId) {
        String query = "SELECT pg_advisory_lock(:lockId)";

        Query nativeQuery = entityManager.createNativeQuery(query);
        nativeQuery.setParameter("lockId", lockId);

        nativeQuery.executeUpdate();
    }

    public void releaseLock(Long lockId) {
        String query = "SELECT pg_advisory_unlock(:lockId)";

        Query nativeQuery = entityManager.createNativeQuery(query);
        nativeQuery.setParameter("lockId", lockId);

        nativeQuery.executeUpdate();
    }
}
