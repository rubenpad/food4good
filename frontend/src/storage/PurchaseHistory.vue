<script setup lang="ts">
import { computed, ref } from "vue";
import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Message from "primevue/message";
import { usePolling } from "../usePolling";
import { usePurchasePage } from "./storage";

const previousCursors = ref<(string | null)[]>([]);
const cursor = ref<string | null>(null);

const { data: page, error, refetch } = usePurchasePage(cursor);
usePolling(refetch);

const purchases = computed(() => page.value?.items ?? []);

function older() {
  previousCursors.value.push(cursor.value);
  cursor.value = page.value?.nextCursor ?? null;
}

function newer() {
  cursor.value = previousCursors.value.pop() ?? null;
}

const time = (value: string) => new Date(value).toLocaleTimeString();
</script>

<template>
  <section class="purchases" aria-label="Purchase history">
    <h3>Purchase history</h3>
    <Message v-if="error" severity="error">Could not load Purchases: {{ error.message }}</Message>
    <DataTable v-else :value="purchases" data-key="id" size="small" scrollable>
      <template #empty>Nothing bought from the Farmers Market yet</template>
      <Column field="ingredient" header="Ingredient" />
      <Column field="quantity" header="Units" />
      <Column header="Bought">
        <template #body="{ data }">{{ time(data.purchasedAt) }}</template>
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
  </section>
</template>

<style scoped>
.purchases {
  margin-top: 1.5rem;
}

h3 {
  margin: 0 0 0.5rem;
  font-size: 1rem;
  font-weight: 600;
}

.pages {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
</style>
