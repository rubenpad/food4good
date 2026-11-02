<script setup lang="ts">
import { ref, watch } from "vue";
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import InputNumber from "primevue/inputnumber";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import Select from "primevue/select";
import { INGREDIENTS, useSaveRecipe, type Recipe } from "./recipes";

interface IngredientRow {
  ingredient: string | null;
  quantity: number | null;
}

const props = defineProps<{ recipe: Recipe | null }>();
const visible = defineModel<boolean>("visible", { required: true });

const name = ref("");
const preparationTimeSeconds = ref<number | null>(null);
const rows = ref<IngredientRow[]>([]);

const { mutateAsync, isLoading, error, reset } = useSaveRecipe();

watch(visible, (isVisible) => {
  if (!isVisible) return;
  reset();
  name.value = props.recipe?.name ?? "";
  preparationTimeSeconds.value = props.recipe?.preparationTimeSeconds ?? null;
  rows.value = props.recipe
    ? props.recipe.ingredients.map((item) => ({ ...item }))
    : [{ ingredient: null, quantity: 1 }];
});

async function save() {
  try {
    await mutateAsync({
      id: props.recipe?.id,
      details: {
        name: name.value,
        // The API explains anything missing, so the form doesn't repeat its rules.
        preparationTimeSeconds: preparationTimeSeconds.value as number,
        ingredients: rows.value.map((row) => ({
          ingredient: row.ingredient as string,
          quantity: row.quantity as number,
        })),
      },
    });
    visible.value = false;
  } catch {
    // Shown through `error`; the dialog stays open so the Manager can fix it.
  }
}
</script>

<template>
  <Dialog
    v-model:visible="visible"
    modal
    :header="recipe ? `Edit ${recipe.name}` : 'New Recipe'"
    class="recipe-dialog"
  >
    <form class="form" @submit.prevent="save">
      <label class="field">
        <span>Name</span>
        <InputText v-model="name" fluid />
      </label>
      <label class="field">
        <span>Preparation time (seconds)</span>
        <InputNumber v-model="preparationTimeSeconds" :use-grouping="false" fluid />
      </label>
      <fieldset class="ingredients">
        <legend>Ingredients</legend>
        <div v-for="(row, index) in rows" :key="index" class="ingredient-row">
          <Select
            v-model="row.ingredient"
            :options="INGREDIENTS"
            placeholder="Ingredient"
            aria-label="Ingredient"
            fluid
          />
          <InputNumber
            v-model="row.quantity"
            :use-grouping="false"
            aria-label="Quantity"
            input-class="quantity"
          />
          <Button
            type="button"
            label="Remove"
            severity="secondary"
            text
            @click="rows.splice(index, 1)"
          />
        </div>
        <Button
          type="button"
          label="Add Ingredient"
          severity="secondary"
          outlined
          @click="rows.push({ ingredient: null, quantity: 1 })"
        />
      </fieldset>
      <Message v-if="error" severity="error">{{ error.message }}</Message>
      <div class="actions">
        <Button type="button" label="Cancel" severity="secondary" text @click="visible = false" />
        <Button type="submit" label="Save" :loading="isLoading" />
      </div>
    </form>
  </Dialog>
</template>

<style scoped>
.form {
  display: grid;
  gap: 1rem;
}

.field {
  display: grid;
  gap: 0.25rem;
}

.ingredients {
  display: grid;
  gap: 0.5rem;
  margin: 0;
  padding: 0;
  border: 0;
}

.ingredients legend {
  padding: 0 0 0.25rem;
}

.ingredient-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 5rem auto;
  gap: 0.5rem;
  align-items: center;
}

.ingredient-row :deep(.quantity) {
  width: 100%;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}
</style>

<style>
.recipe-dialog {
  width: min(32rem, calc(100vw - 2rem));
}
</style>
