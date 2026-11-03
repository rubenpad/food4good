<script setup lang="ts">
import Card from "primevue/card";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Message from "primevue/message";
import PurchaseHistory from "./PurchaseHistory.vue";
import { useStock } from "./storage";

const { data: stock, error } = useStock();
</script>

<template>
  <section aria-label="Food Storage">
    <Card>
      <template #title>Food Storage</template>
      <template #content>
        <Message v-if="error" severity="error"
          >Could not refresh stock: {{ error.message }}</Message
        >
        <DataTable :value="stock ?? []" data-key="ingredient" size="small" scrollable>
          <Column field="ingredient" header="Ingredient" />
          <Column field="onHand" header="On hand" />
          <Column field="reserved" header="Reserved" />
          <Column field="available" header="Available" />
        </DataTable>
        <PurchaseHistory />
      </template>
    </Card>
  </section>
</template>
