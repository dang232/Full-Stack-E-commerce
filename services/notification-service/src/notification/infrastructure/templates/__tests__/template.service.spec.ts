// Mock fs at module level before any imports — avoids "Cannot redefine property" on CJS non-configurable properties
jest.mock('fs', () => ({
  existsSync: jest.fn(),
  readFileSync: jest.fn(),
}));

import * as fs from 'fs';
import { TemplateService } from '../template.service';

const mockExistsSync = fs.existsSync as jest.Mock;
const mockReadFileSync = fs.readFileSync as jest.Mock;

describe('TemplateService', () => {
  let service: TemplateService;

  beforeEach(() => {
    jest.clearAllMocks();
    // Default: directory candidates exist (so templatesDir resolves), .hbs files don't
    mockExistsSync.mockImplementation((p: unknown) => {
      const s = String(p);
      // Let directory resolution succeed (non-.hbs paths)
      if (!s.endsWith('.hbs')) return true;
      return false;
    });
    service = new TemplateService();
  });

  describe('fallback html', () => {
    it('returns fallback HTML for unknown template name', () => {
      // existsSync already returns false for .hbs — hits fallback
      const html = service.render('nonexistent-template', {
        title: 'Test Title',
        body: 'Test body',
      });

      expect(html).toContain('Test Title');
      expect(html).toContain('Test body');
      expect(html).toContain('VNShop');
    });

    it('includes deepLink anchor in fallback when deepLink provided', () => {
      const html = service.render('nonexistent-template', {
        title: 'Title',
        body: 'Body',
        deepLink: '/orders/123',
      });

      expect(html).toContain('/orders/123');
      expect(html).toContain('href=');
    });

    it('omits deepLink anchor in fallback when deepLink absent', () => {
      const html = service.render('no-link-template', {
        title: 'Title',
        body: 'Body',
      });

      expect(html).not.toContain('href=');
    });
  });

  describe('compile and cache', () => {
    const hbsSource = '<h1>{{title}}</h1><p>{{body}}</p>';

    it('renders a found template with context', () => {
      mockExistsSync.mockReturnValue(true);
      mockReadFileSync.mockReturnValue(hbsSource);

      const html = service.render('order-confirmed', {
        title: 'Order placed',
        body: 'Your order is confirmed.',
      });

      expect(html).toContain('Order placed');
      expect(html).toContain('Your order is confirmed.');
    });

    it('returns cached compiled template on second call', () => {
      mockExistsSync.mockReturnValue(true);
      mockReadFileSync.mockReturnValue(hbsSource);

      service.render('cached-template', { title: 'T', body: 'B' });
      service.render('cached-template', { title: 'T2', body: 'B2' });

      // readFileSync should be called only once (cache hit on second call)
      // The service also calls readFileSync 0 times when the template is already cached
      // Count only the template read (not directory scan reads)
      const templateReadCalls = mockReadFileSync.mock.calls.filter((args) =>
        String(args[0]).endsWith('.hbs'),
      );
      expect(templateReadCalls.length).toBe(1);
    });

    it('falls back to fallback HTML when readFileSync throws', () => {
      mockExistsSync.mockReturnValue(true);
      mockReadFileSync.mockImplementation(() => {
        throw new Error('read error');
      });

      const html = service.render('bad-template', {
        title: 'Fallback Title',
        body: 'Fallback body',
      });

      expect(html).toContain('Fallback Title');
      expect(html).toContain('Fallback body');
    });
  });

  describe('templatesDir resolution', () => {
    it('constructs without throwing when no candidate dirs exist', () => {
      mockExistsSync.mockReturnValue(false);
      expect(() => new TemplateService()).not.toThrow();
    });

    it('uses first existing candidate directory', () => {
      let callCount = 0;
      mockExistsSync.mockImplementation(() => {
        callCount++;
        // First call returns true — first candidate is used
        return callCount === 1;
      });
      expect(() => new TemplateService()).not.toThrow();
    });
  });
});
