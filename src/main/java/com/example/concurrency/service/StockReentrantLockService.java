package com.example.concurrency.service;

import com.example.concurrency.domain.Stock;
import com.example.concurrency.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class StockReentrantLockService {
    private final StockRepository stockRepository;
//    private final Map<Long, ReentrantLock> stockReentrantLockMap = new HashMap<>();
//    private final Map<Long, Condition> stockConditionMap = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition(); // lock 조건 설정 가능

    private final EntityManager entityManager;

    public StockReentrantLockService(StockRepository stockRepository, EntityManager entityManager) {
        this.stockRepository = stockRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public void decreaseProcess(Long id, Long quantity) {
        lock.lock();
        try {
            log.info("{}, Lock 획득, templateId={}", Thread.currentThread().getName(), id);
            Stock stock = stockRepository.findById(id).orElseThrow();
            log.info("재고: {}", stock.getQuantity());
            while (!stock.isPossibleDecrease(quantity)) {
                log.info("{}, 재고 없어...{}, Lock 반납, 대기표 뽑음, templateId={}", Thread.currentThread().getName(), stock.getQuantity(), id);
                condition.await();
                log.info("{}, 시그널 받음, 대기표 반납, templateId={}", Thread.currentThread().getName(), id);
                /*
                문제상황: 다른 트랜잭션에서 업데이트 한 값이 1차 캐시에 남아있어서 새로 업데이트가 안되는 중. 그래서 while문을 계속 통과를 못함.
                (synchronized 에서는 객체의 데이터가 바뀌면 기다리던 스레드에도 flush를 해줬는데... 이건 안해주는것 같음...)
                 */
                entityManager.refresh(stock);
                stock = stockRepository.findById(id).orElseThrow();
            }
            log.info("{}, templateId={}, 재고 감소={}-{}", Thread.currentThread().getName(), id, stock.getQuantity(), quantity);
            stock.decrease(quantity);

            stockRepository.save(stock);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
            log.info("{}, Lock 해제 완료", Thread.currentThread().getName());
        }
    }

    @Transactional
    public void increaseProcess(Long id, Long quantity) {
        lock.lock();
        try {
            log.info("{}, Lock 획득, templateId={}", Thread.currentThread().getName(), id);
            Stock stock = stockRepository.findById(id).orElseThrow();

            log.info("재고: {}", stock.getQuantity());
            log.info("{}, templateId={}, 재고 증가={}+{}", Thread.currentThread().getName(), id, stock.getQuantity(), quantity);
            stock.increase(quantity);

            stockRepository.save(stock);

            log.info("{}, Lock 반납예정 시그널!, templateId={}", Thread.currentThread().getName(), id);
            condition.signal();
        } finally {
            lock.unlock();
            log.info("{}, Lock 해제 완료", Thread.currentThread().getName());
        }
    }

//    private ReentrantLock getStockLock(Long id) {
//        synchronized (stockReentrantLockMap) {
//            ReentrantLock reentrantLock = new ReentrantLock();
//            // condition 생성
//            stockConditionMap.computeIfAbsent(id, templateId -> reentrantLock.newCondition());
//            return stockReentrantLockMap.computeIfAbsent(id, templateId -> new ReentrantLock());
//        }
//    }
//
//    private Condition getCondition(Long id) {
//        synchronized (stockConditionMap) {
//            return stockConditionMap.get(id);
//        }
//    }
}
