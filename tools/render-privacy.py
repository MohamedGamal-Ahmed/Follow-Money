"""Render the app's exact bundled policy as a public, static HTML page (stdlib only)."""
from html import escape
from pathlib import Path

root = Path(__file__).resolve().parents[1]
policy = (root / 'app/src/main/assets/privacy-policy.txt').read_text(encoding='utf-8')
arabic, english = policy.split('\nEnglish\n', 1)
body = '\n'.join(
    '<p>' + escape(paragraph).replace('\n', '<br>') + '</p>'
    for paragraph in arabic.split('\n\n')
)
english_body = '\n'.join(
    '<p>' + escape(paragraph).replace('\n', '<br>') + '</p>'
    for paragraph in english.split('\n\n')
)
page = '''<!doctype html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Follow Money — Privacy Policy / سياسة الخصوصية</title>
<style>body{margin:0;background:#f7f9f7;color:#183b2b;font:16px/1.8 system-ui,sans-serif}main{max-width:840px;margin:auto;padding:24px}p{white-space:normal}h1{font-size:26px}a{color:#245e43}</style>
</head><body><main><h1>Follow Money — سياسة الخصوصية / Privacy Policy</h1>
<nav><a href="#english">English</a> · <a href="#arabic">العربية</a></nav>
<section id="arabic" lang="ar" dir="rtl">''' + body + '''</section>
<section id="english" lang="en" dir="ltr"><h2>English</h2>''' + english_body + '''</section>
</main></body></html>
'''
destination = root / 'support/privacy.html'
destination.write_text(page, encoding='utf-8')
print('Rendered support/privacy.html from bundled app policy.')
(root / 'support/PRIVACY.md').write_text('# ' + policy, encoding='utf-8')
print('Rendered support/PRIVACY.md from the same policy.')
