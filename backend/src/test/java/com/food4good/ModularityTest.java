package com.food4good;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(Food4GoodApplication.class);

    @Test
    void modulesOnlyUseEachOthersPublishedApi() {
        modules.verify();
    }

    @Test
    void hasTheKitchenFoodStorageAndFarmersMarketModules() {
        assertThat(modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
                .containsExactlyInAnyOrder("kitchen", "storage", "market");
    }
}
