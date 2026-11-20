#!/usr/bin/env python3
"""Real-browser UX checks for the generated docs page at phone widths.

`page-check.mjs` proves the page loads; this proves it behaves on a phone:
no horizontal scroll, readable text, collapsible details and no JS errors.
"""
import functools
import http.server
import os
import socketserver
import sys
import threading
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / 'dist'


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass


def chromium_executable(playwright):
    override = os.environ.get('PLAYWRIGHT_CHROMIUM')
    if override:
        return override
    default = Path(playwright.chromium.executable_path)
    if default.exists():
        return str(default)
    caches = sorted(
        Path.home().glob('Library/Caches/ms-playwright/chromium_headless_shell-*'),
        reverse=True)
    for cache in caches:
        for exe in cache.glob('**/chrome-headless-shell'):
            if os.access(exe, os.X_OK):
                return str(exe)
    raise RuntimeError(
        'No Chromium found. Install it with `playwright install chromium` or '
        'set PLAYWRIGHT_CHROMIUM to a headless shell executable.')


def main():
    if not (DIST / 'index.html').exists():
        print('MOBILE CHECK FAIL — dist/index.html missing; run npm run build')
        sys.exit(1)

    handler = functools.partial(QuietHandler, directory=str(DIST))
    server = socketserver.ThreadingTCPServer(('127.0.0.1', 0), handler)
    port = server.server_address[1]
    threading.Thread(target=server.serve_forever, daemon=True).start()

    failures = 0
    try:
        from playwright.sync_api import sync_playwright
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(
                headless=True, executable_path=chromium_executable(playwright))
            for width, height in ((360, 740), (390, 844)):
                label = f'{width}x{height}'
                page = browser.new_page(viewport={'width': width, 'height': height})
                errors = []
                page.on('console', lambda msg: errors.append(msg.text)
                        if msg.type == 'error' else None)
                page.on('pageerror', lambda err: errors.append(str(err)))
                response = page.goto(f'http://127.0.0.1:{port}/',
                                     wait_until='load', timeout=15000)
                case_errors = []
                if response is None or response.status != 200:
                    case_errors.append(f'HTTP status {response.status if response else "none"}')
                if not page.locator('h1').is_visible():
                    case_errors.append('h1 is not visible')
                overflow = page.evaluate(
                    'document.documentElement.scrollWidth > window.innerWidth')
                if overflow:
                    case_errors.append('page scrolls horizontally')
                font_size = page.evaluate(
                    'parseFloat(getComputedStyle(document.body).fontSize)')
                if font_size < 16:
                    case_errors.append(f'body font is {font_size}px (< 16px)')
                details = page.locator('details')
                if details.count() < 2:
                    case_errors.append(f'only {details.count()} details blocks')
                if details.count():
                    summary = details.first.locator('summary')
                    if details.first.get_attribute('open') is not None:
                        case_errors.append('details start expanded (tall page)')
                    summary.click()
                    if details.first.get_attribute('open') is None:
                        case_errors.append('details did not expand on tap')
                    summary.click()
                    if details.first.get_attribute('open') is not None:
                        case_errors.append('details did not collapse on tap')
                if errors:
                    case_errors.append(f'JS errors: {"; ".join(errors[:3])}')
                page.close()
                if case_errors:
                    failures += 1
                    print(f'FAIL  mobile.{label}')
                    for error in case_errors:
                        print(f'        - {error}')
                else:
                    print(f'PASS  mobile.{label}')
            browser.close()
    except Exception as error:
        failures += 1
        print(f'FAIL  mobile.browser — {type(error).__name__}: {error}')
    finally:
        server.shutdown()
        server.server_close()

    if failures:
        print(f'MOBILE CHECK FAILED — {failures} viewport(s)')
        sys.exit(1)
    print('MOBILE CHECK PASS — 2 viewports, no overflow, details toggle')


if __name__ == '__main__':
    main()
