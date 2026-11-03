<script setup lang="ts">
import { computed, ref } from "vue";
import Button from "primevue/button";
import Card from "primevue/card";
import InputNumber from "primevue/inputnumber";
import Message from "primevue/message";
import { formatDuration } from "../formatDuration";
import { useKitchen } from "../kitchen/kitchen";
import { usePlaceOrders } from "./orders";

const quantity = ref<number | null>(1);
const { mutate, data: result, error, isLoading } = usePlaceOrders();
const { data: kitchen } = useKitchen();

const outcome = computed(() => {
  const placed = result.value;
  if (!placed) return null;
  const wait = formatDuration(placed.estimatedWaitSeconds);
  if (placed.accepted === 0) {
    return {
      severity: "warn",
      text: `The Kitchen is full: accepted 0 of ${placed.requested}. Estimated wait to clear the Backlog: ${wait}.`,
    };
  }
  if (placed.accepted < placed.requested) {
    return {
      severity: "warn",
      text: `Accepted ${placed.accepted} of ${placed.requested}. The rest would go past the Maximum wait. Estimated wait: ${wait}.`,
    };
  }
  return {
    severity: "success",
    text: `Accepted ${placed.accepted} of ${placed.requested}. Estimated wait: ${wait}.`,
  };
});
</script>

<template>
  <section aria-label="Place Orders">
    <Card>
      <template #title>Place Orders</template>
      <template #content>
        <form class="form" @submit.prevent="mutate(quantity)">
          <label class="field">
            <span>Quantity</span>
            <InputNumber v-model="quantity" :use-grouping="false" />
          </label>
          <Button type="submit" label="Place Orders" :loading="isLoading" />
          <span v-if="kitchen" class="estimate">
            Estimate: about {{ kitchen.estimatedCapacity }} more Orders fit right now
          </span>
        </form>
        <Message v-if="error" severity="error" class="result">{{ error.message }}</Message>
        <Message v-else-if="outcome" :severity="outcome.severity" class="result">
          {{ outcome.text }}
        </Message>
      </template>
    </Card>
  </section>
</template>

<style scoped>
.form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.75rem;
}

.field {
  display: grid;
  gap: 0.25rem;
}

.estimate {
  align-self: center;
  color: var(--p-text-muted-color);
}

.result {
  margin-top: 0.75rem;
}
</style>
