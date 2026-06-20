<script setup>
import { ref, onMounted } from 'vue'

const visible = ref(false)

onMounted(() => {
  const observer = new IntersectionObserver(
    ([entry]) => { if (entry.isIntersecting) visible.value = true },
    { threshold: 0.2 }
  )
  observer.observe(document.getElementById('codigo'))
})

const techs = ['Kotlin', 'Jetpack Compose', 'Room', 'Hilt', 'Jsoup', 'WebView']
</script>

<template>
  <section id="codigo" class="py-24 sm:py-32 bg-white">
    <div class="max-w-5xl mx-auto px-6">
      <div class="flex flex-col lg:flex-row items-center gap-12 lg:gap-20">
        <div class="flex-1" :class="['fade-in', { visible }]">
          <span class="text-sm font-semibold text-mint-500 uppercase tracking-wider">Código Aberto</span>
          <h2 class="text-3xl sm:text-4xl font-bold text-gray-900 mt-3 mb-4">
            Construído com transparência
          </h2>
          <p class="text-gray-500 text-lg leading-relaxed mb-6">
            TchelvisNovels é um projeto open-source sob licença MIT.
            Sinta-se livre para explorar, contribuir, ou adaptar para suas próprias necessidades.
          </p>

          <div class="flex flex-wrap gap-2 mb-8">
            <span
              v-for="tech in techs"
              :key="tech"
              class="px-3 py-1.5 bg-lavender-100 rounded-full text-sm font-medium text-gray-700"
            >{{ tech }}</span>
          </div>

          <div class="flex items-center gap-4">
            <a href="https://github.com/fabriciof807/NovelReader"
               target="_blank"
               rel="noopener noreferrer"
               class="inline-flex items-center gap-2 bg-gray-900 text-white px-6 py-3 rounded-full text-sm font-medium hover:bg-gray-700 transition-all shadow-lg hover:shadow-xl">
              <svg class="w-5 h-5" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
              Ver no GitHub
            </a>
            <a href="/" class="text-gray-500 hover:text-gray-800 transition-colors text-sm font-medium">
              MIT License
            </a>
          </div>
        </div>

        <div class="flex-1 w-full max-w-md" :class="['fade-in', { visible }]" :style="{ transitionDelay: '0.2s' }">
          <div class="bg-gray-900 rounded-2xl p-6 shadow-2xl">
            <div class="flex items-center gap-2 mb-4">
              <span class="w-3 h-3 rounded-full bg-rose-400"></span>
              <span class="w-3 h-3 rounded-full bg-yellow-400"></span>
              <span class="w-3 h-3 rounded-full bg-mint-400"></span>
              <span class="text-gray-400 text-xs ml-2">TchelvisNovels.kt</span>
            </div>
            <pre class="text-sm text-gray-300 font-mono leading-relaxed overflow-x-auto"><span class="text-rose-300">class</span> <span class="text-sky-300">TchelvisNovelsApp</span> : <span class="text-rose-300">Application</span>() {
    <span class="text-gray-400">@HiltAndroidApp</span>
    <span class="text-rose-300">override fun</span> <span class="text-yellow-300">onCreate</span>() {
        <span class="text-rose-300">super</span>.onCreate()
        <span class="text-gray-400">// Configuração inicial</span>
    }
}</pre>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
