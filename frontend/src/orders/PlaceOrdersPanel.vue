<script setup lang="ts">
import { ref } from "vue";
import Button from "primevue/button";
import Card from "primevue/card";
import InputNumber from "primevue/inputnumber";
import Message from "primevue/message";
import { usePlaceOrders } from "./orders";

const quantity = ref<number | null>(1);
const { mutate, data: result, error, isLoading } = usePlaceOrders();
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
        </form>
        <Message v-if="error" severity="error" class="result">{{ error.message }}</Message>
        <Message v-else-if="result" severity="success" class="result">
          Accepted {{ result.accepted }} of {{ result.requested }}
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

.result {
  margin-top: 0.75rem;
}
</style>
