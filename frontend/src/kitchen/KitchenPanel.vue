<script setup lang="ts">
import Card from "primevue/card";
import Message from "primevue/message";
import { formatDuration } from "../formatDuration";
import { usePolling } from "../usePolling";
import OrderColumn from "./OrderColumn.vue";
import { useKitchen } from "./kitchen";

const { data: kitchen, error, refetch } = useKitchen();
usePolling(refetch);
</script>

<template>
  <section aria-label="Kitchen">
    <Card>
      <template #title>
        <div class="title">
          <span>Kitchen</span>
          <span v-if="kitchen" class="cooks">
            Cooks busy: {{ kitchen.busyCooks }} of {{ kitchen.cooks }}
          </span>
        </div>
      </template>
      <template #content>
        <Message v-if="error" severity="error"
          >Could not refresh the Kitchen: {{ error.message }}</Message
        >
        <p v-if="kitchen" class="backlog">
          Backlog: {{ formatDuration(kitchen.backlogSeconds) }} (estimated) · Maximum wait:
          {{ formatDuration(kitchen.maxWaitSeconds) }}
        </p>
        <div v-if="kitchen" class="columns">
          <OrderColumn
            status="WAITING_FOR_INGREDIENTS"
            title="Waiting for ingredients"
            :total="kitchen.ordersWaitingForIngredients"
          />
          <OrderColumn status="READY" title="Ready" :total="kitchen.ordersReady" />
          <OrderColumn status="PREPARING" title="Preparing" :total="kitchen.ordersPreparing" />
        </div>
      </template>
    </Card>
  </section>
</template>

<style scoped>
.title {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.25rem 1rem;
}

.cooks {
  font-size: 1rem;
  color: var(--p-text-muted-color);
}

.backlog {
  margin: 0 0 1rem;
  color: var(--p-text-muted-color);
}

.columns {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 1.5rem;
}
</style>
