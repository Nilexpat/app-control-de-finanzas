// Pruebas de firestore.rules contra el emulador, via REST y sin dependencias.
// Se lanzan con probar-reglas.cmd (raiz del repositorio).
//
// Las escrituras imitan lo que envia el SDK de Android: `setMerge` es un
// set(..., SetOptions.merge()), y `joinBatch` es el lote de
// FirestoreRepository.joinWithInvitation. Si cambia ese lote, cambiar este.
const PROJECT = 'demo-socio';
const HOST = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080';
const DB = `projects/${PROJECT}/databases/(default)`;
const BASE = `http://${HOST}/v1/${DB}/documents`;

const b64 = o => Buffer.from(JSON.stringify(o)).toString('base64url');
function token(uid) {
  const now = Math.floor(Date.now() / 1000);
  return `${b64({ alg: 'none', typ: 'JWT' })}.${b64({
    sub: uid, user_id: uid, aud: PROJECT, iss: `https://securetoken.google.com/${PROJECT}`,
    iat: now, exp: now + 3600, auth_time: now,
    firebase: { sign_in_provider: 'google.com', identities: {} },
  })}.`;
}
const headers = who => ({
  'Content-Type': 'application/json',
  ...(who === null ? {} : { Authorization: `Bearer ${who === 'OWNER' ? 'owner' : token(who)}` }),
});

function val(v) {
  if (v === null) return { nullValue: null };
  if (typeof v === 'string') return { stringValue: v };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') return { integerValue: String(v) };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(val) } };
  throw new Error('tipo no soportado');
}
const fields = o => Object.fromEntries(Object.entries(o).map(([k, v]) => [k, val(v)]));
const docName = p => `${DB}/documents/${p}`;

// Equivalentes a lo que envia el SDK de Android
const setMerge = (path, data) => ({
  update: { name: docName(path), fields: fields(data) },
  updateMask: { fieldPaths: Object.keys(data) },
});
const updateFields = (path, data) => ({ ...setMerge(path, data), currentDocument: { exists: true } });
const arrayUnion = (path, field, v) => ({
  update: { name: docName(path), fields: {} }, updateMask: { fieldPaths: [] },
  updateTransforms: [{ fieldPath: field, appendMissingElements: { values: [val(v)] } }],
  currentDocument: { exists: true },
});
const increment = (path, field, n) => ({
  update: { name: docName(path), fields: {} }, updateMask: { fieldPaths: [] },
  updateTransforms: [{ fieldPath: field, increment: val(n) }],
  currentDocument: { exists: true },
});
const del = path => ({ delete: docName(path) });

async function commit(who, writes) {
  const r = await fetch(`${BASE}:commit`, { method: 'POST', headers: headers(who), body: JSON.stringify({ writes }) });
  return { ok: r.ok, status: r.status, body: await r.text() };
}
async function get(who, path) {
  const r = await fetch(`${BASE}/${path}`, { headers: headers(who) });
  return { ok: r.ok, status: r.status, body: await r.text() };
}
async function query(who, parent, collection, where) {
  const r = await fetch(`${BASE}${parent ? '/' + parent : ''}:runQuery`, {
    method: 'POST', headers: headers(who),
    body: JSON.stringify({ structuredQuery: { from: [{ collectionId: collection }], ...(where ? { where } : {}) } }),
  });
  const body = await r.text();
  const ok = r.ok && !body.includes('"error"');
  const count = ok ? JSON.parse(body).filter(x => x.document).length : 0;
  return { ok, status: r.status, body, count };
}
const eq = (f, v) => ({ fieldFilter: { field: { fieldPath: f }, op: 'EQUAL', value: val(v) } });
const contains = (f, v) => ({ fieldFilter: { field: { fieldPath: f }, op: 'ARRAY_CONTAINS', value: val(v) } });

let fallos = 0, total = 0;
function check(nombre, res, esperado) {
  total++;
  const bien = res.ok === esperado;
  if (!bien) fallos++;
  console.log(`${bien ? 'OK   ' : 'FALLO'} ${esperado ? 'permite' : 'deniega'}: ${nombre}` +
    (bien ? '' : `\n        -> HTTP ${res.status} ${res.body.slice(0, 300)}`));
}

const G = 'group_aaaa1111', G2 = 'group_bbbb2222';
const ADMIN = 'uidAdmin', ANA = 'uidAna', BETO = 'uidBeto', OTRO = 'uidOtroAdmin';
const futuro = Date.now() + 48 * 3600_000;
const inv = (code, groupId, extra = {}) => ({
  id: code, groupId, code, role: 'SOCIO', expiresAt: futuro, maxUses: 1, usedCount: 0, isRevoked: false, ...extra,
});
const ficha = (groupId, uid, code, extra = {}) => ({
  id: `user_${groupId}_${uid}`, groupId, name: 'Nuevo', email: 'n@x.com', role: 'SOCIO', active: true,
  sharePercentCentecimas: 2000, initialDebtCop: 0, googleUid: uid, updatedAt: Date.now(), inviteCode: code, ...extra,
});
const fichaPath = (g, uid) => `groups/${g}/members/user_${g}_${uid}`;
const joinBatch = (g, uid, code, extra = {}) => [
  setMerge(fichaPath(g, uid), ficha(g, uid, code, extra)),
  arrayUnion(`groups/${g}`, 'memberUids', uid),
  increment(`invitations/${code}`, 'usedCount', 1),
];
const mov = { id: 'mv1', groupId: G, type: 'VENTA', totalCop: 1000, isAnnulled: false };

(async () => {
  // --- creacion de empresa por su administrador ---
  check('crear empresa sin figurar en memberUids', await commit(ADMIN, [setMerge(`groups/${G}`, { id: G, name: 'Mi empresa', memberUids: [], ownerId: ADMIN })]), false);
  check('crear empresa figurando en memberUids', await commit(ADMIN, [setMerge(`groups/${G}`, { id: G, name: 'Mi empresa', memberUids: [ADMIN], ownerId: ADMIN })]), true);
  check('admin crea su ficha (id aleatorio)', await commit(ADMIN, [setMerge(`groups/${G}/members/user_abc12345`, { id: 'user_abc12345', groupId: G, role: 'ADMIN', active: true, googleUid: ADMIN, sharePercentCentecimas: 5000, initialDebtCop: 0 })]), true);
  check('admin crea movimiento', await commit(ADMIN, [setMerge(`groups/${G}/movements/mv1`, mov)]), true);
  check('admin crea cuenta, producto y corte', await commit(ADMIN, [
    setMerge(`groups/${G}/transferAccounts/a1`, { id: 'a1', groupId: G, name: 'Nequi' }),
    setMerge(`groups/${G}/products/p1`, { id: 'p1', groupId: G, name: 'Cafe' }),
    setMerge(`groups/${G}/settlements/s1`, { id: 's1', groupId: G }),
  ]), true);
  check('admin anula movimiento', await commit(ADMIN, [setMerge(`groups/${G}/movements/mv1`, { ...mov, isAnnulled: true })]), true);
  check('admin renombra empresa (set merge completo)', await commit(ADMIN, [setMerge(`groups/${G}`, { id: G, name: 'Renombrada', memberUids: [ADMIN], ownerId: ADMIN })]), true);

  // segunda empresa, de otro administrador
  await commit(OTRO, [setMerge(`groups/${G2}`, { id: G2, name: 'Ajena', memberUids: [OTRO], ownerId: OTRO })]);
  await commit(OTRO, [setMerge('invitations/OTHERG22', inv('OTHERG22', G2))]);

  // --- invitaciones ---
  check('admin crea invitacion', await commit(ADMIN, [setMerge('invitations/CODE2345', inv('CODE2345', G))]), true);
  check('invitacion con id distinto del codigo', await commit(ADMIN, [setMerge('invitations/xxxx', inv('CODE9999', G))]), false);
  check('invitacion ya gastada de inicio... (usedCount != 0)', await commit(ADMIN, [setMerge('invitations/BADC2345', inv('BADC2345', G, { usedCount: 5 }))]), false);
  check('admin crea invitacion para empresa ajena', await commit(ADMIN, [setMerge('invitations/HACK2345', inv('HACK2345', G2))]), false);
  await commit(ADMIN, [setMerge('invitations/EXPD2345', inv('EXPD2345', G, { expiresAt: Date.now() - 1000 }))]);
  await commit(ADMIN, [setMerge('invitations/REVK2345', inv('REVK2345', G, { isRevoked: true }))]);
  await commit(ADMIN, [setMerge('invitations/ADMN2345', inv('ADMN2345', G, { role: 'ADMIN' }))]);
  await commit(ADMIN, [setMerge('invitations/MULT2345', inv('MULT2345', G, { maxUses: 5 }))]);
  check('admin lista invitaciones de su empresa', await query(ADMIN, '', 'invitations', eq('groupId', G)), true);
  check('admin reescribe invitacion (set merge completo)', await commit(ADMIN, [setMerge('invitations/CODE2345', inv('CODE2345', G))]), true);

  // --- alguien de fuera, sin unirse ---
  check('sin autenticar lee empresa', await get(null, `groups/${G}`), false);
  check('sin autenticar lee invitacion', await get(null, 'invitations/CODE2345'), false);
  check('ajeno lee empresa', await get(ANA, `groups/${G}`), false);
  check('ajeno lee movimiento', await get(ANA, `groups/${G}/movements/mv1`), false);
  check('ajeno lista movimientos', await query(ANA, `groups/${G}`, 'movements'), false);
  check('ajeno lista socios', await query(ANA, `groups/${G}`, 'members'), false);
  check('ajeno escribe movimiento', await commit(ANA, [setMerge(`groups/${G}/movements/mv1`, { ...mov, totalCop: 1 })]), false);
  check('ajeno borra movimiento', await commit(ANA, [del(`groups/${G}/movements/mv1`)]), false);
  check('ajeno lee invitacion por codigo', await get(ANA, 'invitations/CODE2345'), true);
  check('ajeno lista invitaciones de la empresa', await query(ANA, '', 'invitations', eq('groupId', G)), false);
  check('ajeno lista todas las invitaciones', await query(ANA, '', 'invitations'), false);
  check('ajeno lista todas las empresas', await query(ANA, '', 'groups'), false);
  const propias = await query(ANA, '', 'groups', contains('memberUids', ANA));
  check('ajeno consulta SUS empresas (vacio)', { ...propias, ok: propias.ok && propias.count === 0 }, true);
  check('ajeno crea solo su ficha', await commit(ANA, [setMerge(fichaPath(G, ANA), ficha(G, ANA, 'CODE2345'))]), false);
  check('ajeno se anade solo a memberUids', await commit(ANA, [arrayUnion(`groups/${G}`, 'memberUids', ANA)]), false);
  check('ajeno gasta invitacion sin unirse', await commit(ANA, [increment('invitations/CODE2345', 'usedCount', 1)]), false);
  check('ajeno revoca invitacion', await commit(ANA, [updateFields('invitations/CODE2345', { isRevoked: true })]), false);
  check('ajeno amplia usos de invitacion', await commit(ANA, [updateFields('invitations/CODE2345', { maxUses: 99 })]), false);
  check('ajeno: ficha + lista, sin gastar invitacion', await commit(ANA, joinBatch(G, ANA, 'CODE2345').slice(0, 2)), false);
  check('ajeno: lista + invitacion, sin ficha', await commit(ANA, joinBatch(G, ANA, 'CODE2345').slice(1)), false);
  check('ajeno: ficha + invitacion, sin lista', await commit(ANA, [joinBatch(G, ANA, 'CODE2345')[0], joinBatch(G, ANA, 'CODE2345')[2]]), false);

  // --- uniones invalidas ---
  check('union con invitacion caducada', await commit(ANA, joinBatch(G, ANA, 'EXPD2345')), false);
  check('union con invitacion revocada', await commit(ANA, joinBatch(G, ANA, 'REVK2345')), false);
  check('union con invitacion de OTRA empresa', await commit(ANA, joinBatch(G, ANA, 'OTHERG22')), false);
  check('union con codigo inexistente', await commit(ANA, joinBatch(G, ANA, 'NOEXISTE')), false);
  check('union pidiendo rol ADMIN con invitacion de SOCIO', await commit(ANA, joinBatch(G, ANA, 'CODE2345', { role: 'ADMIN' })), false);
  check('union con porcentaje inflado', await commit(ANA, joinBatch(G, ANA, 'CODE2345', { sharePercentCentecimas: 10000 })), false);
  check('union con ficha inactiva', await commit(ANA, joinBatch(G, ANA, 'CODE2345', { active: false })), false);
  check('union con ficha a nombre de otro uid', await commit(ANA, joinBatch(G, ANA, 'CODE2345', { googleUid: BETO })), false);
  check('union gastando 2 usos', await commit(ANA, [...joinBatch(G, ANA, 'CODE2345').slice(0, 2), increment('invitations/CODE2345', 'usedCount', 2)]), false);
  check('union colando a un tercero en la lista', await commit(ANA, [
    joinBatch(G, ANA, 'CODE2345')[0],
    updateFields(`groups/${G}`, { memberUids: [ADMIN, ANA, BETO] }),
    joinBatch(G, ANA, 'CODE2345')[2],
  ]), false);
  check('union expulsando al admin de la lista', await commit(ANA, [
    joinBatch(G, ANA, 'CODE2345')[0],
    updateFields(`groups/${G}`, { memberUids: [ANA] }),
    joinBatch(G, ANA, 'CODE2345')[2],
  ]), false);
  check('union cambiando ademas el nombre de la empresa', await commit(ANA, [
    joinBatch(G, ANA, 'CODE2345')[0],
    updateFields(`groups/${G}`, { memberUids: [ADMIN, ANA], name: 'Hackeada' }),
    joinBatch(G, ANA, 'CODE2345')[2],
  ]), false);
  check('union + escritura de movimiento en el mismo lote', await commit(ANA, [...joinBatch(G, ANA, 'CODE2345'), setMerge(`groups/${G}/movements/mvX`, mov)]), false);
  check('union escribiendo la ficha de OTRO socio', await commit(ANA, [
    setMerge(`groups/${G}/members/user_abc12345`, { role: 'SOCIO' }), ...joinBatch(G, ANA, 'CODE2345'),
  ]), false);

  // --- union valida ---
  check('UNION VALIDA de Ana', await commit(ANA, joinBatch(G, ANA, 'CODE2345')), true);
  check('Ana lee la empresa', await get(ANA, `groups/${G}`), true);
  check('Ana lista movimientos', await query(ANA, `groups/${G}`, 'movements'), true);
  check('Ana lista socios', await query(ANA, `groups/${G}`, 'members'), true);
  const deAna = await query(ANA, '', 'groups', contains('memberUids', ANA));
  check('Ana descubre su empresa por consulta', { ...deAna, ok: deAna.ok && deAna.count === 1 }, true);
  check('Ana registra un movimiento', await commit(ANA, [setMerge(`groups/${G}/movements/mv2`, { ...mov, id: 'mv2' })]), true);
  check('Ana sigue sin leer la empresa ajena', await get(ANA, `groups/${G2}`), false);
  check('Beto reutiliza la invitacion agotada', await commit(BETO, joinBatch(G, BETO, 'CODE2345')), false);
  check('Beto se une con invitacion de rol ADMIN pidiendo ADMIN', await commit(BETO, joinBatch(G, BETO, 'ADMN2345', { role: 'ADMIN' })), true);

  // --- expulsion ---
  check('admin expulsa a Ana (ficha inactiva + lista)', await commit(ADMIN, [
    setMerge(fichaPath(G, ANA), { ...ficha(G, ANA, 'CODE2345'), active: false }),
    updateFields(`groups/${G}`, { memberUids: [ADMIN, BETO] }),
  ]), true);
  check('Ana expulsada lee movimientos', await query(ANA, `groups/${G}`, 'movements'), false);
  check('Ana expulsada escribe movimiento', await commit(ANA, [setMerge(`groups/${G}/movements/mv2`, { ...mov, id: 'mv2', totalCop: 5 })]), false);
  check('Ana expulsada reactiva su ficha', await commit(ANA, [setMerge(fichaPath(G, ANA), ficha(G, ANA, 'CODE2345'))]), false);
  check('Ana expulsada se reanade a la lista', await commit(ANA, [arrayUnion(`groups/${G}`, 'memberUids', ANA)]), false);
  check('Ana expulsada resube el grupo con ella dentro', await commit(ANA, [setMerge(`groups/${G}`, { id: G, name: 'Renombrada', memberUids: [ADMIN, BETO, ANA], ownerId: ADMIN })]), false);
  check('Ana expulsada reusa su invitacion agotada', await commit(ANA, joinBatch(G, ANA, 'CODE2345')), false);
  check('Ana vuelve con una invitacion NUEVA vigente', await commit(ANA, joinBatch(G, ANA, 'MULT2345')), true);

  // --- borrado ---
  check('ajeno borra invitacion', await commit('uidNadie', [del('invitations/MULT2345')]), false);
  check('ajeno borra empresa', await commit('uidNadie', [del(`groups/${G}`)]), false);
  check('admin borra invitacion', await commit(ADMIN, [del('invitations/MULT2345')]), true);
  check('admin borra contenido y empresa', await commit(ADMIN, [del(`groups/${G}/movements/mv1`), del(`groups/${G}/members/user_abc12345`)]), true);
  check('admin borra la empresa', await commit(ADMIN, [del(`groups/${G}`)]), true);

  console.log(`\n${total - fallos}/${total} correctas, ${fallos} fallos`);
  process.exit(fallos ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
