package com.example.concurrency.service;

import com.example.concurrency.domain.Stock;
import com.example.concurrency.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class StockSynchronizedService {

    private final StockRepository stockRepository;

    private final Map<Long, Stock> stockLockMap = new HashMap<>();

    public StockSynchronizedService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Transactional
    public void decreaseProcess(Long id, Long quantity) {
        synchronized (getStockLock(id)) {
            log.info("{}, Lock 획득, templateId={}", Thread.currentThread().getName(), id);
            Stock stock = getStockLock(id);
            while (!stock.isPossibleDecrease(quantity)) {
                try {
                    log.info("{}, 재고 없어... Lock 반납, 대기표 뽑음, templateId={}", Thread.currentThread().getName(), id);
                    stock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                log.info("{}, 시그널 받음, 대기표 반납, templateId={}", Thread.currentThread().getName(), id);
            }

            log.info("{}, templateId={}, 재고 감소={}-{}", Thread.currentThread().getName(), id, stock.getQuantity(), quantity);
            stock.decrease(quantity);

            stockRepository.save(stock);
        }
    }

    @Transactional
    public void increaseProcess(Long id, Long quantity) {
        synchronized (getStockLock(id)) {
            log.info("{}, Lock 획득, templateId={}", Thread.currentThread().getName(), id);
            Stock stock = getStockLock(id);

            log.info("{}, templateId={}, 재고 증가={}+{}", Thread.currentThread().getName(), id, stock.getQuantity(), quantity);
            stock.increase(quantity);

            stockRepository.save(stock);

            log.info("{}, Lock 반납예정 시그널!, templateId={}", Thread.currentThread().getName(), id);
            stock.notifyAll();
        }
    }

    private Stock getStockLock(Long id) {
        synchronized (stockLockMap) {
            return stockLockMap.computeIfAbsent(id, templateId -> stockRepository.findById(id).orElseThrow());
        }
    }
}
