package com.example.concurrency.service;

import com.example.concurrency.domain.Stock;
import com.example.concurrency.repository.StockRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StockReentrantLockServiceTest {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockReentrantLockService stockReentrantLockService;

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

    /** [시나리오] <br>
     * 1. init: 재고가 100이 있던 1번, 2번 상품 <br>
     * 2. decrease1_A: 재고 100 감소, 100 -> 0 <br>
     * 3. decrease1_B: 재고 100 감소 시도하나 불가능하여 await() <br>
     * 4. decrease2_A: 다른 쓰레드들과 경합없이 2번 상품 재고 100 감소, 100 -> 0 <br>
     * 5. increase1_A: 재고 50 증가, 0 -> 50, signal() 보냄 <br>
     * 6. decrease1_B: 재고 재확인 후 조건에 맞지 않아 다시 await() <br>
     * 7. increase1_B: 재고 50 증가, 50 -> 100, signal() 보냄 <br>
     * 8. decrease1_B: 재고 재확인 후 조건에 맞아 재고 100 감소, 100 -> 0 <br>
     */
    @Test
    @DisplayName("ReentrantLock의 Condition을 사용하여 쓰레드 동기화")
    void decrease_increase_decrease_reentrantLock() throws InterruptedException {
        Thread decrease1_A = new Thread(() -> {
            stockReentrantLockService.decreaseProcess(1L, 100L);
        });

        Thread decrease1_B = new Thread(() -> {
            stockReentrantLockService.decreaseProcess(1L, 100L);
        });
        // 아이디 별로 동기화가 다르게 진행됨. (임계영역 진입가능)
        Thread decrease2_A = new Thread(() -> {
            stockReentrantLockService.decreaseProcess(2L, 100L);
        });

        Thread increase1_A = new Thread(() -> {
            stockReentrantLockService.increaseProcess(1L, 50L);
        });

        Thread increase1_B = new Thread(() -> {
            stockReentrantLockService.increaseProcess(1L, 50L);
        });

        decrease1_A.start();
        decrease1_B.start();
        decrease2_A.start();
        Thread.sleep(2000);
        increase1_A.start();
        Thread.sleep(4000); // decrease1_B가 signal을 받은 후 먼저 깨어나기 위해 시간을 더 주기로 함.
        // (만약 해당 텀이 없으면 increase1_B 쓰레드가 먼저 락을 획득하여 0 -> 50 -> 100 이후 decrease1_B가 동작하게 됨. 물론 문제는 없음) 
        // condition.signal() 메서드를 확인해보니, synchronized와 다르게, await()에서 깨어난 쓰레드는 바로 동작을 할 수 있는게 아니라
        // 다른 쓰레드와 마찬가지로 잠금을 다시 획득해야 한다고 함.
        increase1_B.start();

        decrease1_A.join();
        decrease1_B.join();
        decrease2_A.join();
        increase1_A.join();
        increase1_B.join();

        Stock stock1 = stockRepository.findById(1L).orElseThrow();
        Stock stock2 = stockRepository.findById(2L).orElseThrow();
        Assertions.assertThat(stock1.getQuantity()).isEqualTo(100L);
        Assertions.assertThat(stock2.getQuantity()).isEqualTo(0L);
    }
    /** LOG
     * Hibernate: insert into stock (id, product_id, quantity, version) values (default, ?, ?, ?)
     * Hibernate: insert into stock (id, product_id, quantity, version) values (default, ?, ?, ?)
     * 2023-11-01 16:24:37.316  INFO 35076 --- [       Thread-6] c.e.c.service.StockReentrantLockService  : Thread-6, Lock 획득, templateId=1
     * Hibernate: select stock0_.id as id1_0_0_, stock0_.product_id as product_2_0_0_, stock0_.quantity as quantity3_0_0_, stock0_.version as version4_0_0_ from stock stock0_ where stock0_.id=?
     * 2023-11-01 16:24:37.333  INFO 35076 --- [       Thread-6] c.e.c.service.StockReentrantLockService  : 재고: 100
     * 2023-11-01 16:24:37.333  INFO 35076 --- [       Thread-6] c.e.c.service.StockReentrantLockService  : Thread-6, templateId=1, 재고 감소=100-100
     * 2023-11-01 16:24:37.337  INFO 35076 --- [       Thread-6] c.e.c.service.StockReentrantLockService  : Thread-6, Lock 해제 완료
     * 2023-11-01 16:24:37.337  INFO 35076 --- [       Thread-5] c.e.c.service.StockReentrantLockService  : Thread-5, Lock 획득, templateId=1
     * Hibernate: select stock0_.id as id1_0_0_, stock0_.product_id as product_2_0_0_, stock0_.quantity as quantity3_0_0_, stock0_.version as version4_0_0_ from stock stock0_ where stock0_.id=?
     * 2023-11-01 16:24:37.339  INFO 35076 --- [       Thread-5] c.e.c.service.StockReentrantLockService  : 재고: 100
     * 2023-11-01 16:24:37.339  INFO 35076 --- [       Thread-5] c.e.c.service.StockReentrantLockService  : Thread-5, templateId=1, 재고 감소=100-100
     * 2023-11-01 16:24:37.339  INFO 35076 --- [       Thread-5] c.e.c.service.StockReentrantLockService  : Thread-5, Lock 해제 완료
     * 2023-11-01 16:24:37.339  INFO 35076 --- [       Thread-7] c.e.c.service.StockReentrantLockService  : Thread-7, Lock 획득, templateId=2
     * Hibernate: select stock0_.id as id1_0_0_, stock0_.product_id as product_2_0_0_, stock0_.quantity as quantity3_0_0_, stock0_.version as version4_0_0_ from stock stock0_ where stock0_.id=?
     * 2023-11-01 16:24:37.340  INFO 35076 --- [       Thread-7] c.e.c.service.StockReentrantLockService  : 재고: 100
     * 2023-11-01 16:24:37.340  INFO 35076 --- [       Thread-7] c.e.c.service.StockReentrantLockService  : Thread-7, templateId=2, 재고 감소=100-100
     * 2023-11-01 16:24:37.340  INFO 35076 --- [       Thread-7] c.e.c.service.StockReentrantLockService  : Thread-7, Lock 해제 완료
     * Hibernate: update stock set product_id=?, quantity=?, version=? where id=?
     * Hibernate: update stock set product_id=?, quantity=?, version=? where id=?
     * Hibernate: update stock set product_id=?, quantity=?, version=? where id=?
     * 2023-11-01 16:24:39.310  INFO 35076 --- [       Thread-8] c.e.c.service.StockReentrantLockService  : Thread-8, Lock 획득, templateId=1
     * Hibernate: select stock0_.id as id1_0_0_, stock0_.product_id as product_2_0_0_, stock0_.quantity as quantity3_0_0_, stock0_.version as version4_0_0_ from stock stock0_ where stock0_.id=?
     * 2023-11-01 16:24:39.311  INFO 35076 --- [       Thread-8] c.e.c.service.StockReentrantLockService  : 재고: 0
     * 2023-11-01 16:24:39.311  INFO 35076 --- [       Thread-8] c.e.c.service.StockReentrantLockService  : Thread-8, templateId=1, 재고 증가=0+50
     * 2023-11-01 16:24:39.311  INFO 35076 --- [       Thread-8] c.e.c.service.StockReentrantLockService  : Thread-8, Lock 반납예정 시그널!, templateId=1
     * 2023-11-01 16:24:39.311  INFO 35076 --- [       Thread-8] c.e.c.service.StockReentrantLockService  : Thread-8, Lock 해제 완료
     * Hibernate: update stock set product_id=?, quantity=?, version=? where id=?
     * 2023-11-01 16:24:43.319  INFO 35076 --- [       Thread-9] c.e.c.service.StockReentrantLockService  : Thread-9, Lock 획득, templateId=1
     * Hibernate: select stock0_.id as id1_0_0_, stock0_.product_id as product_2_0_0_, stock0_.quantity as quantity3_0_0_, stock0_.version as version4_0_0_ from stock stock0_ where stock0_.id=?
     * 2023-11-01 16:24:43.320  INFO 35076 --- [       Thread-9] c.e.c.service.StockReentrantLockService  : 재고: 50
     * 2023-11-01 16:24:43.320  INFO 35076 --- [       Thread-9] c.e.c.service.StockReentrantLockService  : Thread-9, templateId=1, 재고 증가=50+50
     * 2023-11-01 16:24:43.320  INFO 35076 --- [       Thread-9] c.e.c.service.StockReentrantLockService  : Thread-9, Lock 반납예정 시그널!, templateId=1
     * 2023-11-01 16:24:43.320  INFO 35076 --- [       Thread-9] c.e.c.service.StockReentrantLockService  : Thread-9, Lock 해제 완료
     * Hibernate: update stock set product_id=?, quantity=?, version=? where id=?
     * Hibernate: select stock0_.id as id1_0_0_, stock0_.product_id as product_2_0_0_, stock0_.quantity as quantity3_0_0_, stock0_.version as version4_0_0_ from stock stock0_ where stock0_.id=?
     * Hibernate: select stock0_.id as id1_0_0_, stock0_.product_id as product_2_0_0_, stock0_.quantity as quantity3_0_0_, stock0_.version as version4_0_0_ from stock stock0_ where stock0_.id=?
     * Hibernate: select stock0_.id as id1_0_, stock0_.product_id as product_2_0_, stock0_.quantity as quantity3_0_, stock0_.version as version4_0_ from stock stock0_
     * Hibernate: delete from stock where id=?
     * Hibernate: delete from stock where id=?
     */

}