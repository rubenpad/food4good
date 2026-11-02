<script setup lang="ts">
import { computed, ref, watch } from "vue";
import Button from "primevue/button";
import Card from "primevue/card";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Message from "primevue/message";
import Select from "primevue/select";
import Tag from "primevue/tag";
import { usePolling } from "../usePolling";
import { ORDER_STATUSES, useOrderPage, type OrderStatus } from "./orders";

const status = ref<OrderStatus | null>(null);
// The cursors of the pages before this one, so Newer can step back.
const previousCursors = ref<(string | null)[]>([]);
const cursor = ref<string | null>(null);

const { data: page, error, refetch } = useOrderPage(status, cursor);
usePolling(refetch);

watch(status, () => {
  previousCursors.value = [];
  cursor.value = null;
});

const orders = computed(() => page.value?.items ?? []);

function older() {
  previousCursors.value.push(cursor.value);
  cursor.value = page.value?.nextCursor ?? null;
}

function newer() {
  cursor.value = previousCursors.value.pop() ?? null;
}

const statusOf = (value: OrderStatus) => ORDER_STATUSES.find((item) => item.value === value)!;
const time = (value: string) => new Date(value).toLocaleTimeString();
</script>

<template>
  <section aria-label="Order history">
    <Card>
      <template #title>
        <div class="title">
          <span>Order history</span>
          <Select
            v-model="status"
            :options="ORDER_STATUSES"
            option-label="label"
            option-value="value"
            placeholder="All statuses"
            aria-label="Filter by status"
            show-clear
          />
        </div>
      </template>
      <template #content>
        <Message v-if="error" severity="error">Could not load Orders: {{ error.message }}</Message>
        <DataTable v-else :value="orders" data-key="id" size="small" scrollable>
          <template #empty>No Orders yet</template>
          <Column header="Accepted">
            <template #body="{ data }">{{ time(data.acceptedAt) }}</template>
          </Column>
          <Column field="recipe" header="Recipe" />
          <Column header="Status">
            <template #body="{ data }">
              <Tag
                :severity="statusOf(data.status).severity"
                :value="statusOf(data.status).label"
              />
            </template>
          </Column>
        </DataTable>
        <div class="pages">
          <Button
            label="Newer"
            size="small"
            severity="secondary"
            text
            :disabled="previousCursors.length === 0"
            @click="newer"
          />
          <Button
            label="Older"
            size="small"
            severity="secondary"
            text
            :disabled="!page?.nextCursor"
            @click="older"
          />
        </div>
      </template>
    </Card>
  </section>
</template>

<style scoped>
.title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem 1rem;
}

.pages {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
</style>
