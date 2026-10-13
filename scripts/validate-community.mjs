import fs from 'node:fs';
import assert from 'node:assert/strict';

const kinds = new Set(['Buraco', 'Obstáculo', 'Piso irregular', 'Elevação', 'Troço perigoso']);
const limits = { places: 200, hazards: 500, routes: 30 };
const point = value => {
  assert(Number.isFinite(value.lat) && value.lat >= -90 && value.lat <= 90, 'Invalid latitude');
  assert(Number.isFinite(value.lon) && value.lon >= -180 && value.lon <= 180, 'Invalid longitude');
};
export function validateCatalog(data) {
  assert.equal(data.schema, 'mynavi-gps-community/v1');
  assert.equal(data.publication, 'moderated');
  assert(Number.isInteger(data.revision) && data.revision > 0, 'Revision must be a positive integer');
  assert(Number.isFinite(Date.parse(data.updatedAtUtc)), 'Invalid update timestamp');
  const ids = new Set();
  for (const [key, limit] of Object.entries(limits)) {
    assert(Array.isArray(data[key]) && data[key].length <= limit, `Invalid ${key} list`);
    for (const item of data[key]) {
      assert(typeof item.id === 'string' && item.id.startsWith('github:') && item.id.length <= 80, 'Invalid community ID');
      assert(!ids.has(item.id), 'Duplicate ID'); ids.add(item.id);
      if (key !== 'routes') point(item);
      if (key === 'hazards') {
        assert(kinds.has(item.kind), 'Unknown hazard kind');
        assert(typeof item.note === 'string' && item.note.length <= 300, 'Invalid note');
        assert(Number.isSafeInteger(item.created) && item.created >= 0, 'Invalid observation timestamp');
      } else {
        assert(typeof item.name === 'string' && item.name.trim().length > 0 && item.name.length <= 120, 'Invalid name');
      }
      if (key === 'routes') {
        assert(item.recommended === true, 'Public routes must be recommended');
        assert(Array.isArray(item.points) && item.points.length >= 2 && item.points.length <= 10000, 'Invalid route geometry');
        item.points.forEach(point);
        if (item.steps) {
          assert(Array.isArray(item.steps) && item.steps.length <= 2000);
          assert(Number.isFinite(item.distance) && item.distance >= 0);
          assert(Number.isFinite(item.duration) && item.duration >= 0);
          let previous = -1;
          for (const step of item.steps) {
            point(step);
            assert(Number.isFinite(step.startsAt) && step.startsAt >= previous && step.startsAt <= item.distance + 10, 'Invalid step order');
            assert(typeof step.instruction === 'string' && step.instruction.length <= 400);
            previous = step.startsAt;
          }
        }
      }
    }
  }
  assert(Buffer.byteLength(JSON.stringify(data)) <= 1500000, 'Catalog exceeds mobile cache limit');
  return ids.size;
}

if (process.argv.includes('--test')) {
  const empty = { schema: 'mynavi-gps-community/v1', publication: 'moderated', revision: 1,
    updatedAtUtc: '2026-10-13T00:00:00Z', places: [], hazards: [], routes: [] };
  assert.equal(validateCatalog(empty), 0);
  const valid = structuredClone(empty);
  valid.places.push({ id: 'github:issue-1', name: 'Fixture only', lat: 0, lon: 0 });
  assert.equal(validateCatalog(valid), 1);
  const invalid = structuredClone(valid); invalid.places[0].lat = 200;
  assert.throws(() => validateCatalog(invalid));
  const duplicate = structuredClone(valid); duplicate.places.push({ ...valid.places[0] });
  assert.throws(() => validateCatalog(duplicate));
  const unreviewed = structuredClone(empty); unreviewed.publication = 'proposal';
  assert.throws(() => validateCatalog(unreviewed));
  console.log('Community validator tests passed.');
}
const file = process.argv.find(arg => arg.endsWith('.json')) || 'community/catalog.json';
console.log(`Validated ${validateCatalog(JSON.parse(fs.readFileSync(file, 'utf8')))} approved community entries.`);
