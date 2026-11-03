/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        navy: { 950: '#070b1a', 900: '#0b1020', 800: '#111833', 700: '#1a2447', 600: '#243059' },
        brand: { DEFAULT: '#7c5cff', 400: '#9b82ff', 500: '#7c5cff', 600: '#6a47f0' },
        accent: { DEFAULT: '#3b82f6', 400: '#60a5fa' },
        ludo: { red: '#ef4444', green: '#22c55e', yellow: '#facc15', blue: '#3b82f6' },
      },
      fontFamily: { sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'] },
      boxShadow: { glow: '0 0 24px rgba(124,92,255,.35)' },
      keyframes: {
        shimmer: { '100%': { transform: 'translateX(100%)' } },
        pop: { '0%': { transform: 'scale(.9)', opacity: 0 }, '100%': { transform: 'scale(1)', opacity: 1 } },
        slideIn: { '0%': { transform: 'translateY(8px)', opacity: 0 }, '100%': { transform: 'translateY(0)', opacity: 1 } },
      },
      animation: { pop: 'pop .18s ease-out', slideIn: 'slideIn .2s ease-out' },
    },
  },
  plugins: [],
};
