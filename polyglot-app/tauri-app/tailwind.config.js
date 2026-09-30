/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        accent: 'var(--accent)',
      },
      fontSize: {
        scaled: 'calc(1rem * var(--text-scale))',
      },
    },
  },
  plugins: [],
}