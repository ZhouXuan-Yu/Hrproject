import { createApp } from 'vue';
import App from './App.vue';
import { router } from './router/index.js';
import { prefetchWorkbenchData } from './services/dataPrefetch.js';

// Register global components
import BaseAccordion from './components/BaseAccordion.vue';
import BaseModal from './components/BaseModal.vue';
import StatusBadge from './components/StatusBadge.vue';

const app = createApp(App);
app.use(router);
app.component('BaseAccordion', BaseAccordion);
app.component('BaseModal', BaseModal);
app.component('StatusBadge', StatusBadge);
app.mount('#app');

router.isReady().then(() => {
  prefetchWorkbenchData();
});
