<script setup lang="ts">
import Card from "primevue/card";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import Tag from "primevue/tag";
import Message from "primevue/message";
import { formatDuration, useRecipes } from "./recipes";

const { data: recipes, error, isPending } = useRecipes();
</script>

<template>
  <Card>
    <template #title>Recipes</template>
    <template #content>
      <Message v-if="error" severity="error">Could not load Recipes: {{ error.message }}</Message>
      <DataTable v-else :value="recipes ?? []" :loading="isPending" data-key="id" size="small">
        <Column field="name" header="Recipe" />
        <Column header="Ingredients">
          <template #body="{ data }">
            <div class="ingredients">
              <Tag
                v-for="item in data.ingredients"
                :key="item.ingredient"
                severity="secondary"
                :value="`${item.ingredient} × ${item.quantity}`"
              />
            </div>
          </template>
        </Column>
        <Column header="Preparation time">
          <template #body="{ data }">{{ formatDuration(data.preparationTimeSeconds) }}</template>
        </Column>
      </DataTable>
    </template>
  </Card>
</template>

<style scoped>
.ingredients {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}
</style>
