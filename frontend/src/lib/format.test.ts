import { linkCitations } from './format';

describe('linkCitations', () => {
  it('links only citations that belong to a known source', () => {
    expect(linkCitations('A [1] and B [7].', new Set([1]))).toBe(String.raw`A [\[1\]](#cite-1) and B [7].`);
  });

  it('leaves code and existing links untouched', () => {
    const md = 'Use `arr[1]`:\n```js\nx[1]\n```\nSee [1](https://x.y) and [1].';
    expect(linkCitations(md, new Set([1])))
      .toBe('Use `arr[1]`:\n```js\nx[1]\n```\nSee [1](https://x.y) and ' + String.raw`[\[1\]](#cite-1).`);
  });
});
