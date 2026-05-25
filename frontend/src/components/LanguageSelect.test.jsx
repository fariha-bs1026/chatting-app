import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import LanguageSelect from './LanguageSelect';

describe('LanguageSelect', () => {
  it('renders supported languages and reports changes', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<LanguageSelect language="en" onChange={onChange} />);

    const select = screen.getByLabelText('Language');

    expect(select).toHaveValue('en');
    expect(screen.getByRole('option', { name: 'English' })).toBeInTheDocument();

    await user.selectOptions(select, 'bn');

    expect(onChange).toHaveBeenCalledWith('bn');
  });
});
