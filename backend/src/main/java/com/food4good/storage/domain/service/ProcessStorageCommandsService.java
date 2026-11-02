package com.food4good.storage.domain.service;

import com.food4good.kitchen.domain.ports.in.MarkOrderReadyUseCase;
import com.food4good.shared.domain.ports.out.TransactionPort;
import com.food4good.storage.domain.model.ReservationLine;
import com.food4good.storage.domain.model.StorageCommand;
import com.food4good.storage.domain.ports.in.ProcessStorageCommandsUseCase;
import com.food4good.storage.domain.ports.out.ReservationRepositoryPort;
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
    private final MarkOrderReadyUseCase markOrderReady;
    private final TransactionPort transactions;

    public ProcessStorageCommandsService(
            StockRepositoryPort stock,
            ReservationRepositoryPort reservations,
            StorageCommandRepositoryPort commands,
            MarkOrderReadyUseCase markOrderReady,
            TransactionPort transactions) {
        this.stock = stock;
        this.reservations = reservations;
        this.commands = commands;
        this.markOrderReady = markOrderReady;
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
                }
                commands.markDone(command.id());
            });
        } catch (RuntimeException e) {
            log.error("Storage command {} failed", command, e);
            transactions.inTransaction(() -> commands.recordFailure(command.id(), e.toString(), MAX_ATTEMPTS));
        }
    }

    private void reserve(UUID orderId) {
        var lines = reservations.findLines(orderId).stream().map(this::reserveFromAvailable).toList();
        if (lines.stream().allMatch(ReservationLine::isFilled)) {
            markOrderReady.markReady(orderId);
        }
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
