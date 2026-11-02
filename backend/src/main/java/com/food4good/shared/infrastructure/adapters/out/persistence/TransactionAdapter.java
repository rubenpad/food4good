package com.food4good.shared.infrastructure.adapters.out.persistence;

import com.food4good.shared.domain.ports.out.TransactionPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class TransactionAdapter implements TransactionPort {

    private final TransactionTemplate transactions;

    TransactionAdapter(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
