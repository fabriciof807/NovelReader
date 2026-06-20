/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts}'],
  theme: {
    extend: {
      colors: {
        lavender: { 50: '#f8f5fc', 100: '#E8E0F0', 200: '#d4c4e8', 300: '#b89fd6', 400: '#9c7ac4', 500: '#8258b0', 600: '#6a4590', 700: '#553872', 800: '#472f5e', 900: '#3b274e' },
        sky: { 50: '#f0f7fe', 100: '#D0E4F5', 200: '#a8ccec', 300: '#75aede', 400: '#4a8ecd', 500: '#2c72b8', 600: '#1f5a9a', 700: '#1b487d', 800: '#1c3d66', 900: '#1b3455' },
        rose: { 50: '#fef5f8', 100: '#F5E0E8', 200: '#edc5d4', 300: '#e09cb6', 400: '#cf6d93', 500: '#be4c78', 600: '#a93566', 700: '#8e2854', 800: '#772548', 900: '#67233e' },
        mint: { 50: '#f2fbf5', 100: '#D4EDDA', 200: '#a8dbb8', 300: '#72c28c', 400: '#41a364', 500: '#26874c', 600: '#1a6b3b', 700: '#165530', 800: '#154428', 900: '#123823' },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
