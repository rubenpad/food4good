import { createApp } from "vue";
import { createPinia } from "pinia";
import { PiniaColada } from "@pinia/colada";
import PrimeVue from "primevue/config";
import Aura from "@primeuix/themes/aura";
import App from "./App.vue";
import "./style.css";

createApp(App)
  .use(createPinia())
  .use(PiniaColada)
  .use(PrimeVue, { theme: { preset: Aura } })
  .mount("#app");
