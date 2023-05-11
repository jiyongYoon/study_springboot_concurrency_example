package com.example.concurrency.service;

import com.example.concurrency.domain.Stock;
import com.example.concurrency.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW) // 새로운 트랜잭션 생성
    public synchronized void decrease(Long id, Long quantity) {

        Stock stock = stockRepository.findById(id).orElseThrow(RuntimeException::new);

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }


    @Transactional
    public void decreaseWithPessimisticLock(Long id, Long quantity) {
        Stock stock = stockRepository.findByIdWithPessimisticLock(id);

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }

    @Transactional
    public void decreaseWithOptimisticLock(Long id, Long quantity) {
        Stock stock = stockRepository.findByIdWithOptimisticLock(id);

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }
}
