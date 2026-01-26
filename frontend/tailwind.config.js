/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'boxy-dark': '#1e1e1e',
        'boxy-panel': '#252526',
        'boxy-accent': '#007acc',
      }
    },
  },
  plugins: [],
}
