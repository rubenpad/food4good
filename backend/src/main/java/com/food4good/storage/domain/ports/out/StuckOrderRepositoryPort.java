package com.food4good.storage.domain.ports.out;

import com.food4good.storage.domain.model.StuckOrder;
import java.util.List;

public interface StuckOrderRepositoryPort {

    /**
     * Orders waiting for ingredients that are short of something, with no pending storage command
     * and no live market job, least repaired first. Includes Orders already at {@code maxRepairs},
     * so giving up on them can be reported.
     */
    List<StuckOrder> findStuck(int maxRepairs, int limit);
}
