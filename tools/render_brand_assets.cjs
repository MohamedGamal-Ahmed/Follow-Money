/* Developer-only conversion; sharp is supplied by Codex's runtime, not the Android app. */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const sharp = require(process.env.FOLLOW_MONEY_SHARP || 'sharp');
const root = path.resolve(__dirname, '..');
const catalogDir = path.join(root, 'docs', 'brand-catalog-2026-10-10');
const records = JSON.parse(fs.readFileSync(path.join(catalogDir, 'catalog.json'), 'utf8'));
const output = path.join(root, 'app', 'src', 'main', 'res', 'drawable-nodpi');
fs.mkdirSync(output, { recursive: true });
(async () => {
  const manifest = [];
  for (const r of records.filter(r => r.asset_path)) {
    const original = fs.readFileSync(path.join(catalogDir, r.asset_path));
    if (crypto.createHash('sha256').update(original).digest('hex') !== r.sha256) throw Error(`Original changed: ${r.entity_id}`);
    const target = path.join(output, `brand_${r.entity_id}.png`);
    // Preserve the seven already bundled assets; render only newly added logos.
    const existing = ['starbucks','carrefour','mcdonalds','kfc','uber','netflix','spotify'].includes(r.entity_id);
    if (!existing || !fs.existsSync(target)) {
      let bytes = original;
      if (r.color_hex && r.asset_path.endsWith('.svg')) {
        bytes = Buffer.from(original.toString('utf8').replace('<svg ', `<svg fill="#${r.color_hex}" `));
      }
      await sharp(bytes, { density: 192 }).resize(256, 256, { fit: 'contain', background: { r:0,g:0,b:0,alpha:0 } }).png().toFile(target);
    }
    manifest.push({ entity_id:r.entity_id, resource:`brand_${r.entity_id}`, original_sha256:r.sha256,
      output_sha256:crypto.createHash('sha256').update(fs.readFileSync(target)).digest('hex'), preserved_existing:existing,
      source_page:r.source_page, rights_status:r.rights_status });
  }
  fs.writeFileSync(path.join(catalogDir,'android-assets.json'), JSON.stringify(manifest,null,2)+'\n');
  console.log(`Validated and bundled ${manifest.length} logos (7 existing preserved).`);
})().catch(error => {console.error(error.message);process.exitCode=1;});
