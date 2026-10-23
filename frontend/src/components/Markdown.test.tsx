import { fireEvent, render, screen } from '@testing-library/react';

import { Markdown } from './Markdown';

describe('Markdown', () => {
  it('renders citation chips that report clicks', () => {
    const onCitation = vi.fn();
    render(<Markdown content="Paris is the capital [2]." citations={new Set([2])} onCitation={onCitation} />);
    fireEvent.click(screen.getByRole('button', { name: 'Source 2' }));
    expect(onCitation).toHaveBeenCalledWith(2);
  });

  it('does not render raw HTML or javascript links', () => {
    const { container } = render(<Markdown content={'<img src=x onerror="alert(1)"> [click](javascript:alert(1))'} />);
    expect(container.querySelector('img')).toBeNull();
    const link = screen.getByText('click').closest('a');
    expect(link?.getAttribute('href') ?? '').not.toContain('javascript');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer nofollow');
  });
});
