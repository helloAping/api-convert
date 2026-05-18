import { createApp, type Component } from 'vue'
import App from './App.vue'
import router from './router'
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NForm,
  NFormItem,
  NGrid,
  NGridItem,
  NH2,
  NIcon,
  NInput,
  NInputNumber,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NLayoutSider,
  NMenu,
  NMessageProvider,
  NModal,
  NPagination,
  NSelect,
  NSpace,
  NStatistic,
  NSwitch,
  NTag,
  NText,
} from 'naive-ui'

const app = createApp(App)
app.use(router)
const naiveComponents: [string, Component][] = [
  ['NAlert', NAlert],
  ['NButton', NButton],
  ['NCard', NCard],
  ['NDataTable', NDataTable],
  ['NDatePicker', NDatePicker],
  ['NDescriptions', NDescriptions],
  ['NDescriptionsItem', NDescriptionsItem],
  ['NForm', NForm],
  ['NFormItem', NFormItem],
  ['NGrid', NGrid],
  ['NGridItem', NGridItem],
  ['NH2', NH2],
  ['NIcon', NIcon],
  ['NInput', NInput],
  ['NInputNumber', NInputNumber],
  ['NLayout', NLayout],
  ['NLayoutContent', NLayoutContent],
  ['NLayoutHeader', NLayoutHeader],
  ['NLayoutSider', NLayoutSider],
  ['NMenu', NMenu],
  ['NMessageProvider', NMessageProvider],
  ['NModal', NModal],
  ['NPagination', NPagination],
  ['NSelect', NSelect],
  ['NSpace', NSpace],
  ['NStatistic', NStatistic],
  ['NSwitch', NSwitch],
  ['NTag', NTag],
  ['NText', NText],
]

naiveComponents.forEach(([name, component]) => {
  app.component(name, component)
})
app.mount('#app')
