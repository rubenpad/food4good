<script setup lang="ts">
import { ref } from "vue";
import Button from "primevue/button";
import Card from "primevue/card";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Message from "primevue/message";
import Tag from "primevue/tag";
import RecipeDialog from "./RecipeDialog.vue";
import { formatDuration, useRecipes, type Recipe } from "./recipes";

const { data: recipes, error, isPending } = useRecipes();

const dialogVisible = ref(false);
const editing = ref<Recipe | null>(null);

function openDialog(recipe: Recipe | null) {
  editing.value = recipe;
  dialogVisible.value = true;
}
</script>

<template>
  <section aria-label="Recipes">
    <Card>
      <template #title>
        <div class="title">
          <span>Recipes</span>
          <Button label="New Recipe" size="small" @click="openDialog(null)" />
        </div>
      </template>
      <template #content>
        <Message v-if="error" severity="error">Could not load Recipes: {{ error.message }}</Message>
        <DataTable
          v-else
          :value="recipes ?? []"
          :loading="isPending"
          data-key="id"
          size="small"
          scrollable
        >
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
          <Column header="">
            <template #body="{ data }">
              <Button
                label="Edit"
                size="small"
                severity="secondary"
                text
                :aria-label="`Edit ${data.name}`"
                @click="openDialog(data)"
              />
            </template>
          </Column>
        </DataTable>
      </template>
    </Card>
  </section>
  <RecipeDialog v-model:visible="dialogVisible" :recipe="editing" />
</template>

<style scoped>
.title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.ingredients {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}
</style>
