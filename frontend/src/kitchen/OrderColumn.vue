<script setup lang="ts">
import { computed, ref } from "vue";
import { useOrderPage, type OrderStatus } from "../orders/orders";

const SHOWN = 8;

const props = defineProps<{ status: OrderStatus; title: string; total: number }>();

const { data: page } = useOrderPage(ref(props.status), ref(null));

const shown = computed(() => (page.value?.items ?? []).slice(0, SHOWN));
const hidden = computed(() => Math.max(0, props.total - shown.value.length));
</script>

<template>
  <section class="column" :aria-label="title">
    <h3>{{ title }} ({{ total }})</h3>
    <ul>
      <li v-for="order in shown" :key="order.id">{{ order.recipe }}</li>
    </ul>
    <p v-if="hidden > 0" class="more">and {{ hidden }} more</p>
  </section>
</template>

<style scoped>
.column h3 {
  margin: 0 0 0.5rem;
  font-size: 1rem;
  font-weight: 600;
}

ul {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 0.25rem;
}

.more {
  margin: 0.25rem 0 0;
  color: var(--p-text-muted-color);
}
</style>
