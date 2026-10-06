"""PRÉVIA-ESTÚDIO DE PEÇAS DO MOD (regra de aprovação visual).

Lê `preview_entries()` de um gerador canônico — o MESMO modelo e as MESMAS
texturas que entram no jogo — e monta um estúdio three.js autocontido: as
peças lado a lado, com vistas de 3/4, frente, lateral e topo, luz de estúdio
e chão. Nada é gravado no jogo: é a prévia que o usuário aprova ANTES de
rodar o main() do gerador e buildar.

Uso:
  python tools/preview_mobilia_mercado.py            # mobília do salão
  python tools/preview_mobilia_mercado.py caixa      # caixa registradora
"""
import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))

FONTES = {
    'mobilia': ('gen_mobilia_mercado', 'mobilia-salao.html',
                'Mobiliário do salão', 'mobiliário de serviço do salão'),
    'lavadora': ('gen_mobilia_mercado', 'mobilia-salao.html',
                'Mobiliário do salão', 'máquina de lavar frontal'),
    'caixa': ('gen_caixa_mercado', 'caixa-registradora.html',
              'Caixa registradora', 'a peça do caixa/balcão do mercado'),
}
chave = (sys.argv[1] if len(sys.argv) > 1 else 'mobilia').lower()
if chave not in FONTES:
    print('ERRO: fonte desconhecida. Use: ' + ', '.join(FONTES))
    raise SystemExit(1)
MODULO, ARQUIVO, TITULO, ASSUNTO = FONTES[chave]
gerador = __import__(MODULO)

VENDOR = os.path.join(ROOT, 'preview', 'vendor', 'three.min.js')
SAIDA = os.path.join(ROOT, 'preview', ARQUIVO)


HTML = r"""<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>__TITULO__ — estúdio 3D</title>
<style>
:root{--fundo:#15181c;--painel:#1b1f24cc;--borda:#2c333b;--txt:#e8ecef;--sub:#98a3ad;
      --ok:#7fd1a3;--aviso:#ffca6b}
*{box-sizing:border-box}
body{margin:0;background:var(--fundo);color:var(--txt);
     font:14px/1.45 system-ui,Segoe UI,Roboto,sans-serif;overflow:hidden}
#cena{position:fixed;inset:0;width:100%;height:100%}
aside{position:fixed;background:#1b1f24b8;border:1px solid var(--borda);
      border-radius:10px;padding:12px 14px;backdrop-filter:blur(4px)}
#painel{left:14px;top:14px;width:244px;max-height:calc(100vh - 92px);overflow:auto}
#restaurar{position:fixed;left:14px;top:14px;display:none;z-index:15}
#status{position:fixed;right:18px;bottom:14px;color:#9fadb9;font-size:12px}
select{width:100%;background:#232a31;color:var(--txt);border:1px solid var(--borda);padding:6px;border-radius:6px}
@media(max-width:700px){#painel{width:190px;font-size:12px}#rodape{max-width:210px}}
h1{font-size:15px;margin:0 0 3px}
.sub{color:var(--sub);font-size:11.5px;margin-bottom:9px}
label{display:block;font-size:10.5px;letter-spacing:1px;color:var(--sub);
      text-transform:uppercase;margin:11px 0 5px}
.botoes{display:flex;flex-wrap:wrap;gap:6px}
button{background:#232a31;color:var(--txt);border:1px solid var(--borda);
       border-radius:6px;padding:5px 9px;font:inherit;font-size:12px;cursor:pointer}
button:hover{background:#2b333b}
button.ligado{background:#2f4b3d;border-color:#4e8a6a;color:var(--ok)}
.peca{border-top:1px solid var(--borda);margin-top:9px;padding-top:7px}
.peca b{font-size:12.5px}
.peca span{color:var(--sub);font-size:11px}
.mat{font-family:ui-monospace,Consolas,monospace;font-size:10.5px;color:var(--aviso)}
#rodape{left:14px;bottom:14px;right:auto;max-width:330px;font-size:11.5px;color:var(--sub)}
#pele{position:fixed;inset:0;background:#101317f2;z-index:30;overflow:auto;padding:18px;
      display:none}
#pele.on{display:block}
#pele h3{margin:14px 0 8px;font-size:13px}
#pele .grade{display:flex;flex-wrap:wrap;gap:10px}
#pele figure{margin:0;background:#1b1f24;border:1px solid var(--borda);
            border-radius:8px;padding:6px;text-align:center}
#pele img{width:152px;height:152px;image-rendering:pixelated;display:block;
          border-radius:4px;background:
          repeating-conic-gradient(#22262b 0 25%, #191d21 0 50%) 0 0/16px 16px}
#pele figcaption{font-family:ui-monospace,Consolas,monospace;font-size:10px;
                color:var(--sub);margin-top:5px}
</style>
</head>
<body>
<canvas id="cena"></canvas>
<div id="pele"></div>
<button id="restaurar">mostrar painel</button>
<div id="status">carregando geometria e texturas…</div>

<aside id="painel">
  <h1>__TITULO__</h1>
  <div class="sub">Fonte canônica · JSON nativo + texturas 128×128.<br>
    Prévia de aprovação, <b>não captura do Minecraft</b>.</div>
  <label>Enquadramento</label>
  <select id="variante"><option value="0">Conjunto completo</option><option value="1">Equipamentos em detalhe</option></select>

  <label>Vista</label>
  <div class="botoes" id="vistas"></div>
  <div class="botoes" style="margin-top:6px"><button id="esconder">⤢ esconder painel</button>
    <button id="peles">texturas</button><button id="volume">sem textura</button></div>

  <label>Peças</label>
  <div class="botoes" id="pecas"></div>

  <label>Materiais pintados (128×128)</label>
  <div id="materiais"></div>

  <div id="ficha"></div>
</aside>

<aside id="rodape">arrastar <b>esquerdo</b> → girar · <b>scroll</b> → zoom ·
  duplo clique → reenquadrar</aside>

<script>/*__THREE__*/</script>
<script>
"use strict";
const DADOS = __DADOS__;

/* ---------- cena ---------- */
const canvas = document.getElementById("cena");
const renderer = new THREE.WebGLRenderer({canvas, antialias:true,
    preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.outputColorSpace = THREE.SRGBColorSpace;
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x15181c);
scene.fog = new THREE.Fog(0x15181c, 14, 34);
const camera = new THREE.PerspectiveCamera(42, 1, .1, 200);

scene.add(new THREE.HemisphereLight(0xe6eef6, 0x4b515a, 1.2));
const key = new THREE.DirectionalLight(0xfff4e2, 1.1);
key.position.set(4, 7, -5);
scene.add(key);
const fill = new THREE.DirectionalLight(0x9fb8dc, .5);
fill.position.set(-6, 5, 6);
const rim = new THREE.DirectionalLight(0xffffff, .5);
rim.position.set(2, 4, 5); scene.add(rim);
scene.add(fill);
const chao = new THREE.Mesh(new THREE.PlaneGeometry(60, 60),
    new THREE.MeshLambertMaterial({color: 0x1b1f23}));
chao.rotation.x = -Math.PI / 2;
chao.position.y = -0.001;
scene.add(chao);
const grade = new THREE.GridHelper(30, 30, 0x2f3740, 0x222831);
grade.material.transparent = true; grade.material.opacity = .45;
scene.add(grade);

/* ---------- camera orbital ---------- */
const orbita = {alvo: new THREE.Vector3(0, .9, 0),
                esferica: new THREE.Spherical(12.5, Math.PI * .42, Math.PI * .78),
                vai: null};
function aplica(){
  camera.position.setFromSpherical(orbita.esferica).add(orbita.alvo);
  camera.lookAt(orbita.alvo);
}
function enquadra(v){
  orbita.alvo.set(v.alvo[0], v.alvo[1], v.alvo[2]);
  orbita.esferica.set(v.dist, v.phi, v.theta);
  aplica();
}
// as peças têm a FRENTE na face -Z (painel/aro/etiqueta), então a câmera de
// frente fica em -Z: theta = PI
const VISTAS = {
  "3/4":       {alvo:[0,.9,0],   dist:12.5, phi:Math.PI*.42, theta:Math.PI*.78},
  "frente":    {alvo:[0,.5,0],   dist:8.8, phi:Math.PI*.46, theta:Math.PI},
  "lateral":   {alvo:[0,.5,0],   dist:8.8, phi:Math.PI*.46, theta:Math.PI*.5},
  "topo":      {alvo:[0,.5,0],   dist:9,   phi:Math.PI*.06, theta:Math.PI*.5},
  "operador":  {alvo:[0,.45,0],  dist:3.2, phi:Math.PI*.30, theta:Math.PI*.16},
  "peça":      {alvo:[0,.45,0],  dist:3.2, phi:Math.PI*.35, theta:Math.PI*.84},
};
let arrastando = false, ultX = 0, ultY = 0;
canvas.addEventListener("pointerdown", e => {
  arrastando = true; ultX = e.clientX; ultY = e.clientY;
  canvas.setPointerCapture(e.pointerId);
});
canvas.addEventListener("pointerup", e => {
  arrastando = false;
  if (canvas.hasPointerCapture(e.pointerId)) canvas.releasePointerCapture(e.pointerId);
});
canvas.addEventListener("pointermove", e => {
  if (!arrastando) return;
  orbita.esferica.theta -= (e.clientX - ultX) * .006;
  orbita.esferica.phi = Math.max(.05, Math.min(Math.PI - .1,
      orbita.esferica.phi - (e.clientY - ultY) * .006));
  ultX = e.clientX; ultY = e.clientY;
  aplica();
});
canvas.addEventListener("wheel", e => {
  e.preventDefault();
  orbita.esferica.radius = Math.max(1.4, Math.min(26,
      orbita.esferica.radius * (e.deltaY > 0 ? 1.1 : .91)));
  aplica();
}, {passive:false});
canvas.addEventListener("dblclick", () => enquadra(VISTAS["3/4"]));

let semTextura = false, variante = DADOS.some(p=>p.screen) ? 1 : 0;
document.getElementById('variante').value=String(variante);
/* ---------- modelo -> malha ---------- */
const texturas = {};
function carrega(caminho, url){
  return new Promise(resolve => {
    new THREE.TextureLoader().load(url, t => {
      t.colorSpace = THREE.SRGBColorSpace;
      t.magFilter = THREE.NearestFilter;   // pixel art: sem borrar
      t.minFilter = THREE.LinearMipmapLinearFilter;
      t.anisotropy = renderer.capabilities.getMaxAnisotropy();
      texturas[caminho] = t; resolve();
    });
  });
}

const ORDEM_FACES = ["east", "west", "up", "down", "south", "north"];
const cacheMateriais = new Map();
const invisivel = new THREE.MeshBasicMaterial({visible:false});
const argila = new THREE.MeshLambertMaterial({color:0xb8c2cb});
function material(tex){
  if (!tex) throw new Error('Textura ausente no modelo');
  if (!cacheMateriais.has(tex.uuid))
    cacheMateriais.set(tex.uuid, new THREE.MeshLambertMaterial({map:tex, transparent:true, alphaTest:.04}));
  return cacheMateriais.get(tex.uuid);
}
function resolveTex(ref, mapa){
  let nome = ref;
  const visitados = new Set();
  while (nome && nome.startsWith('#')){
    if (visitados.has(nome)) throw new Error('Alias de textura cíclico');
    visitados.add(nome); nome = mapa[nome.slice(1)];
  }
  return texturas[nome];
}
function malhaDo(el, mapa){
  const [x0, y0, z0] = el.from, [x1, y1, z1] = el.to;
  const geo = new THREE.BoxGeometry((x1-x0)/16, (y1-y0)/16, (z1-z0)/16);
  const uvAttr = geo.attributes.uv;
  const mats = ORDEM_FACES.map((f, i) => {
    const def = (el.faces || {})[f];
    if (!def) return invisivel; // jamais inventar uma face ausente
    const uv = def.uv || [0,0,16,16];
    let cantos = [[uv[0]/16,1-uv[1]/16],[uv[2]/16,1-uv[1]/16],
                  [uv[0]/16,1-uv[3]/16],[uv[2]/16,1-uv[3]/16]];
    // Mesma rotação horária das faces do JSON Minecraft.
    const ordem = [0,1,3,2], passos = (def.rotation || 0)/90;
    cantos = [0,1,2,3].map(j => cantos[ordem[(ordem.indexOf(j)+passos)%4]]);
    cantos.forEach((p,j) => uvAttr.setXY(i*4+j,p[0],p[1]));
    return semTextura ? argila : material(resolveTex(def.texture, mapa));
  });
  uvAttr.needsUpdate = true;
  const malha = new THREE.Mesh(geo, mats);
  malha.name = el.name || 'elemento';
  malha.position.set((x0+x1)/32, (y0+y1)/32, (z0+z1)/32);
  if (el.rotation){
    const r = el.rotation, pivot = new THREE.Vector3(...r.origin).divideScalar(16);
    const grupo = new THREE.Group(); grupo.position.copy(pivot);
    malha.position.sub(pivot); grupo.add(malha);
    grupo.rotation[r.axis] = r.angle*Math.PI/180;
    if (r.rescale){
      const s = 1/Math.cos(r.angle*Math.PI/180);
      ['x','y','z'].filter(a=>a!==r.axis).forEach(a=>grupo.scale[a]=s);
    }
    return grupo;
  }
  return malha;
}
function constroi(peca){
  const grupo = new THREE.Group();
  const model = (peca.variants && peca.variants[variante] || {}).model || peca.model;
  for (const el of model.elements) grupo.add(malhaDo(el, model.textures));
  // Exemplo do texto dinâmico em camada SEPARADA; não está pintado no atlas.
  if (peca.screen && !semTextura){
    const c = document.createElement('canvas'); c.width=512; c.height=96;
    const ctx = c.getContext('2d'); ctx.fillStyle='#d6ebc4';
    ctx.font='64px monospace'; ctx.textAlign='center'; ctx.textBaseline='middle';
    ctx.fillText('R$ 128',256,48);
    const t = new THREE.CanvasTexture(c); t.colorSpace=THREE.SRGBColorSpace;
    const s = peca.screen, y = s.centro[1] + (variante===0 ? 16 : 0);
    const texto = new THREE.Mesh(new THREE.PlaneGeometry(s.largura/16,s.altura/16),
      new THREE.MeshBasicMaterial({map:t,transparent:true,depthWrite:false}));
    texto.rotation.y=Math.PI; texto.position.set(s.centro[0]/16,y/16,(s.centro[2]-.005)/16);
    grupo.add(texto);
  }
  // A origem do JSON é a quina do bloco; centralizar sem alterar os dados.
  grupo.position.set(-.5,0,-.5);
  const raiz = new THREE.Group(); raiz.add(grupo); return raiz;
}

/* ---------- monta o palco ---------- */
const palco = new THREE.Group();
scene.add(palco);
const fichas = document.getElementById("ficha");
const grupos = [];

function monta(ids){
  palco.traverse(o=>{if(o.isMesh)o.geometry.dispose();});
  palco.clear();
  grupos.length = 0;
  // o garrafão mostra EM PILHA com o bebedouro (é assim que a peça se monta
  // no salão — armário embaixo, garrafão em cima)
  const semGarrafao = ids.filter(i => i !== "bebedouro_garrafao");
  const iGarrafao = ids.indexOf("bebedouro_garrafao");
  let i = 0;
  for (const id of semGarrafao){
    const peca = DADOS.find(p => p.id === id);
    if (!peca) continue;
    const g = constroi(peca);
    g.position.x = (i - (semGarrafao.length - 1) / 2) * 2.3;
    g.traverse(o => {if (o.isMesh) o.userData.peca = peca.name;});
    palco.add(g);
    grupos.push({peca, grupo: g});
    if (iGarrafao >= 0 && id === "bebedouro_mercado"){
      const gg = constroi(DADOS.find(p => p.id === "bebedouro_garrafao"));
      gg.position.set(g.position.x, 1, 0);
      palco.add(gg);
      grupos.push({peca: DADOS.find(p => p.id === "bebedouro_garrafao"), grupo: gg});
    }
    i++;
  }
  if (iGarrafao >= 0 && !ids.includes("bebedouro_mercado")){
    const gg = constroi(DADOS.find(p => p.id === "bebedouro_garrafao"));
    const x = (semGarrafao.length - 1) / 2 * 2.3;
    gg.position.set(x, 1, 0);
    palco.add(gg);
    grupos.push({peca: DADOS.find(p => p.id === "bebedouro_garrafao"), grupo: gg});
  }
  ajustaVistas();
  document.getElementById('status').textContent = grupos.length + ' conjunto · ' +
    grupos.reduce((n,g)=>n+(((g.peca.variants||[])[variante]||{}).model||g.peca.model).elements.length,0) +
    ' peças em volume' + (DADOS.some(p=>p.screen) ? ' · total ilustrativo' : ' · prévia de aprovação');
  fichas.innerHTML = ids.map(id => {
    const p = DADOS.find(x => x.id === id);
    return '<div class="peca"><b>' + p.name + '</b><br><span>' +
      p.description + '</span></div>';
  }).join("");
}

/* ---------- botoes ---------- */
let visAtiva = DADOS.some(p=>p.screen) ? "operador" : "3/4";
function ajustaVistas(){
  palco.updateMatrixWorld(true);
  const box = new THREE.Box3().setFromObject(palco);
  const centro = box.getCenter(new THREE.Vector3());
  const tamanho = box.getSize(new THREE.Vector3());
  const dist = Math.max(tamanho.x,tamanho.y,tamanho.z)*1.55;
  Object.entries(VISTAS).forEach(([k,v])=>{
    v.alvo=centro.toArray(); v.dist=dist;
    if (k==='3/4') {v.phi=Math.PI*.37;v.theta=DADOS.length>1 ? Math.PI*.92 : Math.PI*.79;}
    if (k==='frente') {v.phi=Math.PI*.48;v.theta=Math.PI;}
    if (k==='lateral') {v.phi=Math.PI*.43;v.theta=Math.PI*.5;}
    if (k==='peça') {v.dist=dist*.85;}
  });
}
document.getElementById('variante').hidden = !DADOS.some(p=>p.variants);
document.getElementById('variante').onchange = e => {
  variante=Number(e.target.value); monta(selecao); enquadra(VISTAS[visAtiva]);
};
document.getElementById('volume').onclick = e => {
  semTextura=!semTextura; e.target.classList.toggle('ligado',semTextura); monta(selecao);
};
function marca(container, chave, ligada){
  document.querySelectorAll("#" + container + " button").forEach(b =>
    b.classList.toggle("ligado", b.dataset.k === chave && !!ligada));
}
document.getElementById("vistas").innerHTML = Object.keys(VISTAS).map(k =>
  '<button data-k="' + k + '">' + k + '</button>').join("");
document.getElementById("vistas").onclick = e => {
  if (!e.target.dataset.k) return;
  visAtiva = e.target.dataset.k;
  enquadra(VISTAS[visAtiva]);
  marca("vistas", visAtiva, true);
};
const escolhePeca=document.createElement('select'); escolhePeca.id='foco';
escolhePeca.innerHTML='<option value="todos">Todas lado a lado</option>' +
  DADOS.filter(p=>p.id!=='bebedouro_garrafao').map(p=>'<option value="'+p.id+'">'+p.name+'</option>').join('');
document.getElementById('pecas').before(escolhePeca);
escolhePeca.onchange=e=>{
  selecao=e.target.value==='todos' ? DADOS.map(p=>p.id) : [e.target.value];
  if(e.target.value==='bebedouro_mercado')selecao.push('bebedouro_garrafao');
  monta(selecao); enquadra(VISTAS[visAtiva]);
  document.querySelectorAll('#pecas button').forEach(b=>b.classList.toggle('ligado',selecao.includes(b.dataset.k)));
};
document.getElementById("pecas").innerHTML = DADOS.map(p =>
  '<button data-k="' + p.id + '">' + p.name + '</button>').join("");
let selecao = DADOS.map(p => p.id);
document.getElementById("pecas").onclick = e => {
  const k = e.target.dataset.k;
  if (!k) return;
  if (selecao.includes(k)){
    if (selecao.length > 1) selecao = selecao.filter(x => x !== k);
  } else selecao.push(k);
  marca("pecas", null, false);
  document.querySelectorAll("#pecas button").forEach(b =>
    b.classList.toggle("ligado", selecao.includes(b.dataset.k)));
  monta(selecao); enquadra(VISTAS[visAtiva]);
};
document.getElementById("materiais").innerHTML = "";
document.getElementById("esconder").onclick = () => {
  document.querySelectorAll("aside").forEach(a => a.style.display = "none");
  document.getElementById('restaurar').style.display='block';
};
document.getElementById('restaurar').onclick = () => {
  document.querySelectorAll('aside').forEach(a=>a.style.display='');
  document.getElementById('restaurar').style.display='none';
};
addEventListener('keydown',e=>{if(e.key==='Escape')document.getElementById('pele').classList.remove('on');});
// A PELE CRUA: as texturas 128 de cada peça lado a lado, no tamanho que
// elas entram no jogo (2x). É por aqui que se julga se a pintura tem
// acabamento de verdade.
document.getElementById("peles").onclick = () => {
  const alvo = document.getElementById("pele");
  if (alvo.classList.contains("on")){ alvo.classList.remove("on"); return; }
  alvo.innerHTML = '<button onclick="document.getElementById(\'pele\').classList.remove(\'on\')">fechar texturas · Esc</button>' + DADOS.map(p =>
    '<h3>' + p.name + '</h3><div class="grade">' +
    Object.keys(p.textures).filter(k => !k.endsWith("_particle")).map(k =>
      '<figure><img src="' + p.textures[k] + '"><figcaption>' +
      k.replace("intoxicantes:block/", "") + '</figcaption></figure>').join("") +
    '</div>').join("");
  alvo.classList.add("on");
};

function resize(){
  const w = document.documentElement.clientWidth || innerWidth;
  const h = document.documentElement.clientHeight || innerHeight;
  if (!w || !h) return;
  renderer.setSize(w, h, false);
  camera.aspect = w / h;
  camera.updateProjectionMatrix();
}
addEventListener("resize", resize);
resize();
requestAnimationFrame(resize);
(function loop(){
  requestAnimationFrame(loop);
  renderer.render(scene, camera);
})();

(async function(){
  const promessa = [];
  for (const peca of DADOS)
    for (const caminho in peca.textures) promessa.push(carrega(caminho, peca.textures[caminho]));
  await Promise.all(promessa);
  document.getElementById("materiais").innerHTML = DADOS.map(p =>
    '<div class="mat">' + Object.keys(p.model.textures)
      .filter(k => k !== "particle").join(" · ") + '</div>').join('<div style="height:6px"></div>');
  monta(selecao);
  document.querySelectorAll("#pecas button").forEach(b => b.classList.add("ligado"));
  marca("vistas", visAtiva, true);
  enquadra(VISTAS[visAtiva]);
  document.getElementById("painel").insertAdjacentHTML("beforeend",
    '<div class="sub" style="margin-top:10px;color:#7fd1a3">Mesma geometria do gerador. Integração no jogo pendente de aprovação.</div>');
})();
</script>
</body>
</html>
"""


def main():
    if not os.path.isfile(VENDOR) or os.path.getsize(VENDOR) < 100000:
        print("ERRO: preview/vendor/three.min.js nao encontrado.")
        return 1
    with open(VENDOR, encoding="utf-8") as f:
        three = f.read()

    # só os DADOS e a lista de peças — a prévia não grava NENHUM recurso
    dados = []
    for entrada in gerador.preview_entries():
        pecas = dict(entrada)
        pecas.pop("category", None)
        pecas.pop("description", None)
        pecas["description"] = entrada.get("description", "")
        dados.append(pecas)

    html = (HTML.replace("/*__THREE__*/", three)
                .replace("__TITULO__", TITULO)
                .replace("__DADOS__", json.dumps(dados, ensure_ascii=False)))
    os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
    with open(SAIDA, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Estúdio gerado: {os.path.relpath(SAIDA, ROOT)} "
          f"({os.path.getsize(SAIDA) // 1024} KB) — {len(dados)} peças, "
          f"{sum(len(p['textures']) for p in dados)} materiais 128")
    return 0


if __name__ == "__main__":
    sys.exit(main())