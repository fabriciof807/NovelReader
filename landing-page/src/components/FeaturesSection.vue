<script setup>
import { ref, onMounted } from 'vue'

const features = [
  {
    icon: 'M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z',
    title: 'Leitura Offline',
    desc: 'Todos os capítulos ficam salvos no seu dispositivo. Leia sem internet, onde e quando quiser.'
  },
  {
    icon: 'M4 6H2v14a2 2 0 002 2h14v-2H4V6zm16-4H8a2 2 0 00-2 2v12a2 2 0 002 2h12a2 2 0 002-2V4a2 2 0 00-2-2zm0 14H8V4h12v12zM10 9h8v2h-8V9zm0 3h4v2h-4v-2zm0-6h8v2h-8V6z',
    title: 'Importação Inteligente',
    desc: 'Importe arquivos HTML e MHT do armazenamento local ou baixe capítulos diretamente de sites de novel.'
  },
  {
    icon: 'M20 8.69V4h-4.69L12 .69 8.69 4H4v4.69L.69 12 4 15.31V20h4.69L12 23.31 15.31 20H20v-4.69L23.31 12 20 8.69zM12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6 6 2.69 6 6-2.69 6-6 6zm0-10c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4z',
    title: 'Múltiplos Temas',
    desc: 'Claro, Escuro, Sépia e Cinza. Personalize fonte, tamanho e espaçamento para uma leitura confortável.'
  },
  {
    icon: 'M15.5 14h-.79l-.28-.27A6.471 6.471 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
    title: 'Busca Poderosa',
    desc: 'Busca por texto completo com FTS4. Encontre qualquer palavra ou frase em todos os seus capítulos.'
  },
]

const visible = ref(false)

onMounted(() => {
  const observer = new IntersectionObserver(
    ([entry]) => { if (entry.isIntersecting) visible.value = true },
    { threshold: 0.2 }
  )
  observer.observe(document.getElementById('recursos'))
})
</script>

<template>
  <section id="recursos" class="py-24 sm:py-32 bg-white">
    <div class="max-w-5xl mx-auto px-6">
      <div class="text-center mb-16" :class="['fade-in', { visible }]">
        <span class="text-sm font-semibold text-rose-400 uppercase tracking-wider">Recursos</span>
        <h2 class="text-3xl sm:text-4xl font-bold text-gray-900 mt-3 mb-4">
          Tudo que você precisa para ler
        </h2>
        <p class="text-gray-500 max-w-xl mx-auto text-lg">
          Focado em simplicidade e desempenho, sem distrações.
        </p>
      </div>

      <div class="grid sm:grid-cols-2 gap-6 sm:gap-8">
        <div
          v-for="(f, i) in features"
          :key="i"
          :class="['fade-in', { visible }]"
          :style="{ transitionDelay: `${i * 0.1}s` }"
          class="group bg-lavender-50/60 rounded-2xl p-6 sm:p-8 hover:bg-lavender-100/60 transition-all duration-300 hover:shadow-lg hover:-translate-y-1"
        >
          <div class="w-12 h-12 bg-gradient-to-br from-rose-200 to-sky-200 rounded-xl flex items-center justify-center mb-5 group-hover:scale-110 transition-transform">
            <svg class="w-6 h-6 text-gray-700" viewBox="0 0 24 24" fill="currentColor"><path :d="f.icon"/></svg>
          </div>
          <h3 class="text-xl font-semibold text-gray-900 mb-2">{{ f.title }}</h3>
          <p class="text-gray-500 leading-relaxed">{{ f.desc }}</p>
        </div>
      </div>
    </div>
  </section>
</template>
