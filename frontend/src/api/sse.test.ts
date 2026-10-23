import { parseSseBuffer } from './sse';

describe('parseSseBuffer', () => {
  it('parses complete events and keeps the unfinished remainder', () => {
    const { events, rest } = parseSseBuffer('event:meta\ndata:{"a":1}\n\nevent:content\ndata:{"delta":"Hel');
    expect(events).toEqual([{ event: 'meta', data: '{"a":1}' }]);
    expect(rest).toBe('event:content\ndata:{"delta":"Hel');
  });

  it('handles CRLF, optional spaces, comments and multi-line data', () => {
    const { events } = parseSseBuffer(': keep-alive\r\n\r\nevent: content\r\ndata: line1\r\ndata: line2\r\n\r\n');
    expect(events).toEqual([{ event: 'content', data: 'line1\nline2' }]);
  });

  it('defaults the event name to message', () => {
    expect(parseSseBuffer('data:x\n\n').events).toEqual([{ event: 'message', data: 'x' }]);
  });
});
