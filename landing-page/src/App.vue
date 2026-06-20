<script setup>
import NavBar from './components/NavBar.vue'
import HeroSection from './components/HeroSection.vue'
import FeaturesSection from './components/FeaturesSection.vue'
import ScreenshotsSection from './components/ScreenshotsSection.vue'
import OpenSourceSection from './components/OpenSourceSection.vue'
import FooterSection from './components/FooterSection.vue'
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <NavBar />
    <main class="flex-1">
      <HeroSection />
      <FeaturesSection />
      <ScreenshotsSection />
      <OpenSourceSection />
    </main>
    <FooterSection />
  </div>
</template>
