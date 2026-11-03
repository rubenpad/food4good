package com.food4good.storage.domain.service;

import com.food4good.kitchen.domain.ports.in.MarkOrderReadyUseCase;
import com.food4good.shared.domain.ports.out.TransactionPort;
import com.food4good.storage.domain.model.MarketShortfall;
import com.food4good.storage.domain.model.ReservationLine;
import com.food4good.storage.domain.model.StorageCommand;
import com.food4good.storage.domain.ports.in.ProcessStorageCommandsUseCase;
import com.food4good.storage.domain.ports.out.MarketJobRepositoryPort;
import com.food4good.storage.domain.ports.out.PurchaseRepositoryPort;
import com.food4good.storage.domain.ports.out.ReservationRepositoryPort;
import com.food4good.storage.domain.ports.out.StockChangesPort;
import com.food4good.storage.domain.ports.out.StockRepositoryPort;
import com.food4good.storage.domain.ports.out.StorageCommandRepositoryPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessStorageCommandsService implements ProcessStorageCommandsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessStorageCommandsService.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;

    private final StockRepositoryPort stock;
    private final ReservationRepositoryPort reservations;
    private final StorageCommandRepositoryPort commands;
    private final MarketJobRepositoryPort marketJobs;
    private final PurchaseRepositoryPort purchases;
    private final MarkOrderReadyUseCase markOrderReady;
    private final StockChangesPort stockChanges;
    private final TransactionPort transactions;

    public ProcessStorageCommandsService(
            StockRepositoryPort stock,
            ReservationRepositoryPort reservations,
            StorageCommandRepositoryPort commands,
            MarketJobRepositoryPort marketJobs,
            PurchaseRepositoryPort purchases,
            MarkOrderReadyUseCase markOrderReady,
            StockChangesPort stockChanges,
            TransactionPort transactions) {
        this.stock = stock;
        this.reservations = reservations;
        this.commands = commands;
        this.marketJobs = marketJobs;
        this.purchases = purchases;
        this.markOrderReady = markOrderReady;
        this.stockChanges = stockChanges;
        this.transactions = transactions;
    }

    @Override
    public int processPending() {
        var pending = commands.findPending(BATCH_SIZE);
        pending.forEach(this::process);
        return pending.size();
    }

    /** The command's effect and its "done" mark commit together, so a crash can't apply it twice. */
    private void process(StorageCommand command) {
        try {
            transactions.inTransaction(() -> {
                switch (command) {
                    case StorageCommand.Reserve reserve -> reserve(reserve.orderId());
                    case StorageCommand.Consume consume -> consume(consume.orderId());
                    case StorageCommand.AddPurchase purchase -> addPurchase(purchase);
                }
                commands.markDone(command.id());
                stockChanges.changed();
            });
        } catch (RuntimeException e) {
            log.error("Storage command {} failed", command, e);
            transactions.inTransaction(() -> commands.recordFailure(command.id(), e.toString(), MAX_ATTEMPTS));
        }
    }

    private void reserve(UUID orderId) {
        var lines = reservations.findLines(orderId).stream().map(this::reserveFromAvailable).toList();
        var shortfalls = lines.stream()
                .filter(line -> !line.isFilled())
                .map(line -> new MarketShortfall(orderId, line.ingredient(), line.shortfall()))
                .toList();
        if (shortfalls.isEmpty()) {
            markOrderReady.markReady(orderId);
        } else {
            marketJobs.insertAll(shortfalls);
        }
    }

    /** What was bought goes to the Order that needed it first; anything beyond that is available to everyone. */
    private void addPurchase(StorageCommand.AddPurchase purchase) {
        purchases.insert(purchase.marketJobId(), purchase.attempt(), purchase.ingredient(), purchase.quantity());
        var lines = reservations.findLines(purchase.orderId());
        var line = lines.stream()
                .filter(candidate -> candidate.ingredient() == purchase.ingredient())
                .findFirst()
                .orElseThrow();
        var units = Math.min(purchase.quantity(), line.shortfall());
        stock.save(stock.find(purchase.ingredient()).receive(purchase.quantity()).reserve(units));
        reservations.save(line.fill(units));
        var others = lines.stream().filter(candidate -> candidate.ingredient() != purchase.ingredient());
        if (units == line.shortfall() && others.allMatch(ReservationLine::isFilled)) {
            markOrderReady.markReady(purchase.orderId());
        }
    }

    private void consume(UUID orderId) {
        reservations.findLines(orderId).stream().filter(line -> !line.consumed()).forEach(line -> {
            stock.save(stock.find(line.ingredient()).consume(line.reserved()));
            reservations.save(line.consume());
        });
    }

    private ReservationLine reserveFromAvailable(ReservationLine line) {
        var current = stock.find(line.ingredient());
        var units = Math.min(line.shortfall(), current.available());
        var filled = line.fill(units);
        stock.save(current.reserve(units));
        reservations.save(filled);
        return filled;
    }
}
