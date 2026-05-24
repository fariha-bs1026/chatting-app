import { Languages } from 'lucide-react';
import { translate } from '../i18n';
import { SUPPORTED_LANGUAGES } from '../language';

function LanguageSelect({ language, onChange }) {
  const label = translate(language, 'language.label');
  return (
    <label className="language-select" title={label}>
      <Languages size={16} aria-hidden="true" />
      <span className="sr-only">{label}</span>
      <select
        value={language}
        onChange={(event) => onChange(event.target.value)}
        aria-label={label}
      >
        {SUPPORTED_LANGUAGES.map((item) => (
          <option key={item.code} value={item.code}>
            {item.label}
          </option>
        ))}
      </select>
    </label>
  );
}

export default LanguageSelect;
