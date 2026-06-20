<script setup>
</script>

<template>
  <footer class="bg-gray-900 text-gray-400 py-12">
    <div class="max-w-5xl mx-auto px-6">
      <div class="flex flex-col sm:flex-row items-center justify-between gap-4">
        <div class="flex items-center gap-2 text-sm">
          <svg class="w-5 h-5 text-mint-400" viewBox="0 0 24 24" fill="currentColor"><path d="M4 6H2v14a2 2 0 002 2h14v-2H4V6zm16-4H8a2 2 0 00-2 2v12a2 2 0 002 2h12a2 2 0 002-2V4a2 2 0 00-2-2zm0 14H8V4h12v12zM10 9h8v2h-8V9zm0 3h4v2h-4v-2zm0-6h8v2h-8V6z"/></svg>
          <span class="text-gray-300 font-medium">TchelvisNovels</span>
          <span class="hidden sm:inline">—</span>
          <span class="hidden sm:inline">MIT License</span>
        </div>

        <div class="flex items-center gap-6 text-sm">
          <a href="https://github.com/fabriciof807/NovelReader"
             target="_blank"
             rel="noopener noreferrer"
             class="hover:text-white transition-colors inline-flex items-center gap-1.5">
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
            GitHub
          </a>
          <span class="text-gray-600">|</span>
          <span>&copy; {{ new Date().getFullYear() }} TchelvisNovels</span>
        </div>
      </div>
    </div>
  </footer>
</template>
