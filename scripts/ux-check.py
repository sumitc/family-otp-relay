#!/usr/bin/env python3
"""UX regression suite for both app pages (Live, People) and their dialogs.

Source-level checks are deliberate: the APK has no instrumentation tests and
no emulator is available. Each case maps to a user-visible behaviour that has
broken before, so a future edit fails the build instead of shipping silently.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = (ROOT / 'source/com/familyrelay/local/MainActivity.java').read_text()
PROTECT = (ROOT / 'source/com/familyrelay/local/UiProtection.java').read_text()

CASES = []


def case(name):
    def register(fn):
        CASES.append((name, fn))
        return fn
    return register


def method(src, name):
    """Return the full body of `void name(...) { ... }` including braces."""
    match = re.search(
        r'\b(?:private|protected|public)\s+(?:void|[A-Z]\w*)\s+' +
        re.escape(name) + r'\s*\([^)]*\)\s*\{', src)
    if not match:
        raise AssertionError(f'method {name}() not found')
    start = match.end() - 1
    depth = 0
    for i in range(start, len(src)):
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                return src[start:i + 1]
    raise AssertionError(f'method {name}() has no closing brace')


def card_block(body, card):
    start = body.index(f'LinearLayout {card} = this.column();')
    end = body.index(f'choices.addView((View){card}', start)
    return body[start:end]


def builders(src):
    out = []
    for match in re.finditer(r'new AlertDialog\.Builder', src):
        end = src.find('.create()', match.start())
        if end >= 0:
            out.append(src[match.start():end + len('.create()')])
    return out


def input_dialogs():
    """Return (method name, method body, builder chain) for input dialogs."""
    out = []
    for name in ('editProfile', 'pasteQr', 'testDetector'):
        body = method(MAIN, name)
        chains = [chain for chain in builders(body) if '.setView(' in chain]
        if not chains:
            raise AssertionError(f'{name}: no AlertDialog with a custom view')
        out.append((name, body, chains[0]))
    return out


# --- People page -----------------------------------------------------------

@case('people.card_order')
def people_card_order():
    body = method(MAIN, 'people')
    errors = []
    specs = [
        ('show', 'SHOW QR', 'to receive OTPs', 'showSub',
         ('this.qrImage(me)', '"Create my QR"')),
        ('scan', 'SCAN QR', 'to forward OTPs', 'scanSub',
         ('\\uD83D\\uDCF7',)),
    ]
    for card, title, subtitle, var, content in specs:
        block = card_block(body, card)
        first_add = next((line.strip() for line in block.splitlines()
                          if '.addView(' in line), '')
        if title not in first_add:
            errors.append(f'{card}: first child must be the title "{title}", '
                          f'got: {first_add or "<none>"}')
        p_title = block.find(f'"{title}"')
        p_sub = block.find(f'"{subtitle}"')
        p_sub_add = block.find(f'{var});', p_sub)
        p_content = min((pos for token in content
                         if (pos := block.find(token, p_title)) >= 0),
                        default=-1)
        if p_title < 0 or p_sub < 0 or p_sub_add < 0:
            errors.append(f'{card}: missing title, subtitle or subtitle addView')
        elif not (p_title < p_sub < p_sub_add < p_content):
            errors.append(f'{card}: expected title > subtitle > content order; '
                          f'positions {p_title}/{p_sub}/{p_sub_add}/{p_content}')
        if re.search(rf'\b{card}\.setGravity\(17\)', block):
            errors.append(f'{card}: card is vertically centred, title drifts '
                          'into the middle')
    return errors


@case('people.qr_inline')
def people_qr_inline():
    body = method(MAIN, 'people')
    errors = []
    if 'this.qrImage(me)' not in body:
        errors.append('SHOW QR no longer renders the QR inline')
    if '"Enlarge"' not in body or 'this.showQr()' not in body:
        errors.append('tapping the inline QR must still enlarge it')
    if '"My contact QR"' not in body:
        errors.append('inline QR needs a content description')
    return errors


@case('people.compact_rows')
def people_compact_rows():
    body = method(MAIN, 'registeredPeople')
    errors = []
    for token in ('person.setOrientation(0)', 'actions.setOrientation(0)',
                  'this.actionButton(', 'this.dp(38)'):
        if token not in body:
            errors.append(f'compact row lost {token}')
    helper = method(MAIN, 'actionButton')
    if 'setContentDescription' not in helper:
        errors.append('compact actions lost their content descriptions')
    if 'SOFT_ORANGE' not in helper or 'ORANGE' not in helper:
        errors.append('compact actions lost the theme colours')
    if '"Forward here"' in body:
        errors.append('legacy full-width "Forward here" button returned')
    return errors


@case('people.ascii_actions')
def people_ascii_actions():
    body = method(MAIN, 'registeredPeople')
    errors = []
    for label in ('"Pause"', '"On"', '"Fwd"', '"Bin"'):
        if label not in body:
            errors.append(f'missing plain ASCII action {label}')
    for stale in ('"||"', '">"', '"->"', '\\u23F8', '\\u25B6', '\\u2192',
                  '\\u2715', '\\uD83D\\uDDD1'):
        if stale in body:
            errors.append(f'non-ASCII / odd action glyph returned: {stale}')
    return errors


@case('people.no_rules_section')
def people_no_rules_section():
    body = method(MAIN, 'registeredPeople')
    errors = []
    for stale in ('FORWARDING RULES', 'Save allowed senders',
                  'Allowed senders (blank = all)', 'new Switch(',
                  'Choose allowed senders', 'Forwarding settings'):
        if stale in body:
            errors.append(f'confusing rules section returned: {stale}')
    return errors


@case('people.detector_divider')
def people_detector_divider():
    body = method(MAIN, 'registeredPeople')
    errors = []
    if 'this.divider()' not in body:
        errors.append('missing divider before secondary tools')
    if '"Try offline detector"' not in body:
        errors.append('offline detector link is missing')
    lines = body.splitlines()
    for i, line in enumerate(lines):
        if '"Try offline detector"' in line:
            previous = '\n'.join(lines[max(0, i - 4):i])
            if 'this.divider()' not in previous:
                errors.append('detector must come directly after the divider')
            break
    return errors


# --- Live page -------------------------------------------------------------

@case('live.single_next_action')
def live_single_next_action():
    body = method(MAIN, 'live')
    errors = []
    primaries = re.findall(
        r'this\.(?:button|actionButton)\("([^"]+)",\s*(?:"[^"]*",\s*)?true,',
        body)
    expected = ['Create my QR', 'Scan contact QR', 'On']
    if primaries != expected:
        errors.append(f'guided actions changed: {primaries} != {expected}')
    if 'new Switch(' in body or 'Forwarding settings' in body:
        errors.append('Live gained a standalone toggle or settings popup')
    if 'this.activity();' not in body:
        errors.append('Live lost its inline activity log')
    return errors


@case('live.status_header')
def live_status_header():
    body = method(MAIN, 'live')
    errors = []
    for token in ('"FORWARDING ON"', '"FORWARDING PAUSED"', 'this.infoRow(',
                  '"To "'):
        if token not in body:
            errors.append(f'status header lost {token}')
    return errors


@case('live.compact_actions')
def live_compact_actions():
    body = method(MAIN, 'live')
    errors = []
    if 'this.actionButton("Pause"' not in body:
        errors.append('Live pause is not the compact People-style action')
    if 'this.actionButton("On"' not in body:
        errors.append('Live turn-on is not the compact People-style action')
    for stale in ('this.button("Pause forwarding"',
                  'this.button("Turn on forwarding"'):
        if stale in body:
            errors.append(f'full-width Live button returned: {stale}')
    return errors


@case('live.no_page_title')
def live_no_page_title():
    body = method(MAIN, 'live')
    errors = []
    if 'this.text("Live"' in body or 'setTitle(' in body:
        errors.append('Live has a redundant big page title')
    return errors


# --- Dialogs: "action button vanishes after typing" class ------------------

@case('dialog.input_has_actions')
def dialog_input_has_actions():
    errors = []
    dialogs = input_dialogs()
    if len(dialogs) != 3:
        errors.append(f'expected 3 input dialogs, found {len(dialogs)}')
    for name, _, chain in dialogs:
        if '.setPositiveButton(' not in chain:
            errors.append(f'{name}: input dialog has no action button')
        if '.setNegativeButton(' not in chain and '"Done"' not in chain:
            errors.append(f'{name}: input dialog has no dismiss button')
        if ', null)' not in chain:
            errors.append(f'{name}: action must not auto-dismiss on bad input')
    return errors


@case('dialog.keyboard_resize')
def dialog_keyboard_resize():
    errors = []
    if 'SOFT_INPUT_ADJUST_RESIZE' not in PROTECT:
        errors.append('dialogs do not resize for the keyboard; action buttons '
                      'can be pushed off screen while typing')
    if 'window.setSoftInputMode(' not in PROTECT:
        errors.append('UiProtection.show does not apply the soft-input mode')
    if 'dialog.show()' not in PROTECT:
        errors.append('UiProtection.show no longer shows the dialog')
    if 'dialog.show()' in MAIN:
        errors.append('a dialog bypasses UiProtection.show')
    return errors


@case('dialog.validation_keeps_button')
def dialog_validation_keeps_button():
    errors = []
    for name, body, _ in input_dialogs():
        if 'getButton(-1).setOnClickListener' not in body:
            errors.append(f'{name}: validation must use a custom action '
                          'listener so invalid input keeps the dialog open')
        catches = [m.group(1) for m in re.finditer(
            r'catch\s*\([^)]*\)\s*\{([^{}]*)\}', body)]
        if name in ('editProfile', 'pasteQr'):
            if not catches:
                errors.append(f'{name}: no input validation branch')
            for catch in catches:
                if 'dismiss(' in catch or 'render()' in catch:
                    errors.append(f'{name}: a validation error hides the dialog')
                if 'setError(' not in catch:
                    errors.append(f'{name}: a validation error gives no inline hint')
        if name == 'testDetector' and 'result.setText(' not in body:
            errors.append('testDetector: no visible result for typed input')
    return errors


@case('dialog.no_hiding_or_watcher')
def dialog_no_hiding_or_watcher():
    errors = []
    if re.search(r'setVisibility\s*\(\s*View\.(GONE|INVISIBLE)', MAIN):
        errors.append('a view can be hidden mid-flow; buttons must stay visible')
    if 'addTextChangedListener' in MAIN or 'TextWatcher' in MAIN:
        errors.append('a text watcher can mutate the form while typing')
    return errors


# --- Navigation and startup ------------------------------------------------

@case('nav.two_tabs')
def nav_two_tabs():
    errors = []
    if 'new String[]{"Live", "People"}' not in MAIN:
        errors.append('navigation is no longer exactly Live + People')
    if 'store.contacts().isEmpty() ? "People" : "Live"' not in MAIN:
        errors.append('first run must open People until a contact is added')
    return errors


def main():
    failures = 0
    for name, fn in CASES:
        errors = fn()
        if errors:
            failures += 1
            print(f'FAIL  {name}')
            for error in errors:
                print(f'        - {error}')
        else:
            print(f'PASS  {name}')
    total = len(CASES)
    if failures:
        print(f'UX CHECK FAILED — {failures}/{total} cases')
        sys.exit(1)
    print(f'UX CHECK PASS — {total} cases (Live, People, dialogs, navigation)')


if __name__ == '__main__':
    main()
