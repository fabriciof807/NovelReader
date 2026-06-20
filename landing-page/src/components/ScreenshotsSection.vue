<script setup>
import { ref, onMounted } from 'vue'

const images = [
  '/src/assets/Screenshot_20260619_004009.png',
  '/src/assets/Screenshot_20260619_004114.png',
  '/src/assets/Screenshot_20260619_124913.png',
]

const screenshots = [
  { label: 'Biblioteca', desc: 'Organize seus romances em uma biblioteca com busca e filtros.' },
  { label: 'Leitor', desc: 'WebView otimizado com busca, bookmarks e suporte a temas.' },
  { label: 'Importação', desc: 'Importe da web ou de arquivos locais com um toque.' },
]

const current = ref(0)
const visible = ref(false)

onMounted(() => {
  const observer = new IntersectionObserver(
    ([entry]) => { if (entry.isIntersecting) visible.value = true },
    { threshold: 0.2 }
  )
  observer.observe(document.getElementById('screenshots'))

  setInterval(() => { current.value = (current.value + 1) % screenshots.length }, 4000)
})
</script>

<template>
  <section id="screenshots" class="py-24 sm:py-32 bg-gradient-to-b from-lavender-50 to-white">
    <div class="max-w-5xl mx-auto px-6">
      <div class="text-center mb-16" :class="['fade-in', { visible }]">
        <span class="text-sm font-semibold text-sky-400 uppercase tracking-wider">Screenshots</span>
        <h2 class="text-3xl sm:text-4xl font-bold text-gray-900 mt-3 mb-4">
          Uma prévia do aplicativo
        </h2>
        <p class="text-gray-500 max-w-xl mx-auto text-lg">
          Interface limpa e intuitiva, pensada para leitura.
        </p>
      </div>

      <div class="flex flex-col items-center gap-8" :class="['fade-in', { visible }]">
        <div class="relative w-64 sm:w-72 aspect-[9/19] bg-gradient-to-b from-gray-800 to-gray-900 rounded-[2.5rem] shadow-2xl p-3 border-4 border-gray-700">
          <div class="w-full h-full rounded-[2rem] overflow-hidden bg-white flex items-center justify-center">
            <img
              :src="images[current]"
              alt="Screenshot do TchelvisNovels"
              class="w-full h-full object-cover"
            />
          </div>
        </div>

        <div class="flex items-center gap-2">
          <button
            v-for="(_, i) in screenshots"
            :key="i"
            @click="current = i"
            :class="[
              'w-2.5 h-2.5 rounded-full transition-all duration-300',
              current === i ? 'bg-gray-800 w-6' : 'bg-gray-300 hover:bg-gray-400'
            ]"
          ></button>
        </div>

        <div class="text-center">
          <p class="text-xl font-semibold text-gray-900">{{ screenshots[current].label }}</p>
          <p class="text-gray-500 mt-1">{{ screenshots[current].desc }}</p>
        </div>
      </div>
    </div>
  </section>
</template>
