<script setup>
</script>

<template>
  <section class="relative min-h-screen flex items-center justify-center overflow-hidden bg-gradient-to-br from-lavender-100 via-sky-50 to-rose-100 pt-16">
    <div class="absolute inset-0 opacity-30">
      <div class="absolute top-1/4 left-1/4 w-72 h-72 bg-rose-200 rounded-full mix-blend-multiply filter blur-3xl animate-pulse"></div>
      <div class="absolute bottom-1/4 right-1/4 w-96 h-96 bg-sky-200 rounded-full mix-blend-multiply filter blur-3xl animate-pulse" style="animation-delay: 1s"></div>
      <div class="absolute top-1/3 right-1/3 w-64 h-64 bg-lavender-300 rounded-full mix-blend-multiply filter blur-3xl animate-pulse" style="animation-delay: 2s"></div>
    </div>

    <div class="relative z-10 max-w-4xl mx-auto px-6 text-center">
      <div class="inline-flex items-center gap-2 bg-white/60 backdrop-blur-sm rounded-full px-4 py-1.5 text-sm text-gray-600 mb-8 shadow-sm">
        <span class="w-2 h-2 bg-mint-400 rounded-full animate-pulse"></span>
        Código Aberto — MIT License
      </div>

      <h1 class="text-5xl sm:text-6xl md:text-7xl font-bold text-gray-900 leading-tight mb-6 tracking-tight">
        TchelvisNovels<br>
        <span class="inline-block mt-2" style="background: linear-gradient(to right, #fda4af, #38bdf8); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; color: transparent;">Offline</span>
      </h1>

      <p class="text-lg sm:text-xl text-gray-600 max-w-2xl mx-auto mb-10 leading-relaxed">
        TchelvisNovels é um leitor de romances offline-first para Android.
        Importe arquivos HTML/MHT ou baixe capítulos direto da web.
        Todos os seus dados ficam no seu dispositivo.
      </p>

      <div class="flex flex-col sm:flex-row items-center justify-center gap-4">
        <a href="#recursos"
           class="inline-flex items-center gap-2 bg-gray-900 text-white px-8 py-3.5 rounded-full text-base font-medium hover:bg-gray-700 transition-all shadow-lg hover:shadow-xl">
          Explorar Recursos
          <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 14l-7 7m0 0l-7-7m7 7V3"/></svg>
        </a>
        <a href="https://github.com/fabriciof807/NovelReader"
           target="_blank"
           rel="noopener noreferrer"
           class="inline-flex items-center gap-2 bg-white/70 backdrop-blur-sm text-gray-800 px-8 py-3.5 rounded-full text-base font-medium hover:bg-white transition-all shadow-sm hover:shadow-md border border-gray-200/50">
          <svg class="w-5 h-5" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
          Ver no GitHub
        </a>
      </div>

      <div class="mt-16 flex items-center justify-center gap-8 text-sm text-gray-500">
        <span class="flex items-center gap-1.5">
          <svg class="w-4 h-4 text-mint-400" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>
          100% Offline
        </span>
        <span class="flex items-center gap-1.5">
          <svg class="w-4 h-4 text-mint-400" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>
          Código Aberto
        </span>
        <span class="flex items-center gap-1.5">
          <svg class="w-4 h-4 text-mint-400" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>
          Sem Anúncios
        </span>
      </div>
    </div>
  </section>
</template>
