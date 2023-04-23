package com.example.concurrency.facade;

import com.example.concurrency.service.StockService;
import org.springframework.stereotype.Service;

@Service
public class OptimisticLockStockFacade {

    private final StockService stockService;

    public OptimisticLockStockFacade(StockService stockService) {
        this.stockService = stockService;
    }

    public void decreaseWithOptimisticLock(Long id, Long quantity) throws InterruptedException {
        while (true) {
            try {
                stockService.decreaseWithOptimisticLock(id, quantity);
                break;
            } catch (Exception e) {
                Thread.sleep(50);
            }
        }
    }
}
