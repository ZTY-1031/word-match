import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import HomeView from './views/HomeView.vue'
import RoomView from './views/RoomView.vue'
import PlayView from './views/PlayView.vue'
import ResultView from './views/ResultView.vue'
import './style.css'

const router = createRouter({
  // hash 模式：打包后直接丢进 Spring Boot 的 static，不需要服务端 history 回退配置
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/room/:code', name: 'room', component: RoomView },
    { path: '/room/:code/play', name: 'play', component: PlayView },
    { path: '/room/:code/result', name: 'result', component: ResultView },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

createApp(App).use(router).mount('#app')
