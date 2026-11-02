<script setup lang="ts">
import RecipesPanel from "./recipes/RecipesPanel.vue";
</script>

<template>
  <header class="header">
    <h1>food4good</h1>
    <p>Donation event dashboard</p>
  </header>
  <main class="dashboard">
    <RecipesPanel />
  </main>
</template>

<style scoped>
.header {
  padding: 1.5rem 1rem 0.5rem;
  max-width: 72rem;
  margin: 0 auto;
}

.header h1 {
  margin: 0;
}

.header p {
  margin: 0.25rem 0 0;
  color: var(--p-text-muted-color);
}

.dashboard {
  display: grid;
  gap: 1rem;
  padding: 1rem;
  max-width: 72rem;
  margin: 0 auto;
}
</style>
