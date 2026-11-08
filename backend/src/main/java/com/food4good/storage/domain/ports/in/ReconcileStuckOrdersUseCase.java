package com.food4good.storage.domain.ports.in;

/**
 * Restarts work for Orders that nothing is working on anymore, after a defect stopped it. Only the
 * stock writer may call this, between commands, so it never races with them.
 */
public interface ReconcileStuckOrdersUseCase {

    void reconcile();
}
