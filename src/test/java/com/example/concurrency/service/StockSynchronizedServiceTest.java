package com.example.concurrency.service;

import com.example.concurrency.domain.Stock;
import com.example.concurrency.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StockSynchronizedServiceTest {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockSynchronizedService stockSynchronizedService;

    @BeforeEach
    public void before() {
        Stock stock = new Stock(1L, 100L);
        Stock stock2 = new Stock(2L, 100L);

        stockRepository.saveAndFlush(stock);
        stockRepository.saveAndFlush(stock2);
    }

    @AfterEach
    public void after() {
        stockRepository.deleteAll();
    }
    @Test
    @DisplayName("synchronized 와 while() 조건문을 활용하여 쓰레드 동기화")
    void decrease_increase_decrease_synchronized() throws InterruptedException {
        Thread decreaseA = new Thread(() -> {
            stockSynchronizedService.decreaseProcess(1L, 100L);
        });

        Thread decreaseB = new Thread(() -> {
            stockSynchronizedService.decreaseProcess(1L, 100L);
        });
        // 아이디 별로 동기화가 다르게 진행됨. (임계영역 진입가능)
        Thread decreaseC = new Thread(() -> {
            stockSynchronizedService.decreaseProcess(2L, 100L);
        });

        Thread increaseA = new Thread(() -> {
            stockSynchronizedService.increaseProcess(1L, 50L);
        });

        Thread increaseB = new Thread(() -> {
            stockSynchronizedService.increaseProcess(1L, 50L);
        });

        decreaseA.start();
        decreaseB.start();
        decreaseC.start();
        Thread.sleep(2000);
        increaseA.start();
        increaseB.start();

        decreaseA.join();
        decreaseB.join();
        decreaseC.join();
        increaseA.join();
        increaseB.join();
    }

}