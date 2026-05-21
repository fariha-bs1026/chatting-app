import { Moon, Sun } from 'lucide-react';

function ThemeToggle({ theme, onToggle }) {
  const isDark = theme === 'dark';
  return (
    <button
      className="theme-toggle"
      type="button"
      onClick={onToggle}
      title={isDark ? 'Switch to white mode' : 'Switch to dark mode'}
      aria-label={isDark ? 'Switch to white mode' : 'Switch to dark mode'}
    >
      {isDark ? <Sun size={16} aria-hidden="true" /> : <Moon size={16} aria-hidden="true" />}
      <span>{isDark ? 'White' : 'Dark'}</span>
    </button>
  );
}

export default ThemeToggle;
