#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gen_dinheiro_preview.py — PRÉVIA 3D da família R$ (SNC Adventures).

Gera `preview/previa-dinheiro-3d.html`: estúdio Three.js autocontido
(offline — three.min.js + texturas REAIS embutidas em base64) para
inspeção e aprovação visual do upgrade da economia, conforme o fluxo
de aprovação do AGENTS.md (prévia ANTES de buildar).

O que o estúdio tem:
- As 8 peças da economia (moeda_1, real do usuário, nota_5..nota_200)
  como objetos 3D REAIS: a nota é uma folha fina (BoxGeometry 2.37:1)
  com a textura de 1024×432 em cima e embaixo; a moeda é um cilindro
  serrilhado com a textura 512×512 mapeada no disco e na borda.
- Câmera orbital (arrastar = girar, scroll = zoom, botão direito = pan)
  escrita à mão — sem dependência de OrbitControls externo.
- Vistas rápidas: frente/verso inclinado/deitada/lateral/3/4 topo;
  ficha da peça (valor facial, tamanho da textura, resolução real).
- "Carteira" — as 8 juntas na mesa em leque, pra ver a família inteira.
- Fundo de estúdio, chão com sombra suave, grade técnica discreta.

Fonte: lê as texturas REAIS de
  src/main/resources/assets/intoxicantes/textures/item/
Uso: python tools/gen_dinheiro_preview.py
Saída: preview/previa-dinheiro-3d.html (abrir na aba Preview do Freebuff)
"""
import base64
import io
import json
import os

from PIL import Image

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(RAIZ, "src", "main", "resources", "assets", "intoxicantes",
                   "textures", "item")
VENDOR = os.path.join(RAIZ, "preview", "vendor", "three.min.js")
SAIDA = os.path.join(RAIZ, "preview", "previa-dinheiro-3d.html")

# id -> (nome, valor, tipo, legenda)
PECAS = [
    ("moeda_1", "Moeda de R$ 1", 1, "moeda", "aço com borda serrilhada"),
    ("real", "Cédula de R$ 2", 2, "nota", "azul-petróleo — a tartaruga-marinha"),
    ("nota_5", "Nota de R$ 5", 5, "nota", "violeta — a garça"),
    ("nota_10", "Nota de R$ 10", 10, "nota", "vermelho — o papagaio"),
    ("nota_20", "Nota de R$ 20", 20, "nota", "ocre — o sagui-leão"),
    ("nota_50", "Nota de R$ 50", 50, "nota", "marrom — a onça-pintada"),
    ("nota_100", "Nota de R$ 100", 100, "nota", "azul — a arara-azul"),
    ("nota_200", "Nota de R$ 200", 200, "nota", "cinza-esverdeado — o lobo-guará"),
    ("nota_500", "Nota de R$ 500", 500, "nota",
     "dourada — o golfinho (só o Wither dropa)"),
]


def embute(id_item):
    caminho = os.path.join(TEX, id_item + ".png")
    with Image.open(caminho) as im:
        im = im.convert("RGBA")
        # v1.2.62: os sprites dos itens são LETTERBOXADOS (1024×1024 com a
        # cédula no meio, pro Minecraft não esticar) — aqui tiro a faixa
        # transparente pra caixa 3D voltar à proporção real da cédula.
        # Só recorta quando o letterbox é evidente (quadrado com conteúdo
        # bem menor) — a moeda (círculo colado nas bordas) fica intacta.
        caixa = im.getbbox()
        if (caixa and im.width == im.height
                and (caixa[3] - caixa[1]) < im.height * 0.9):
            im = im.crop(caixa)
        w, h = im.size
        buf = io.BytesIO()
        im.save(buf, "PNG")
        b64 = base64.b64encode(buf.getvalue()).decode()
    return {"id": id_item, "w": w, "h": h, "data": b64}


HTML = r"""<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>SNC Adventures — Estúdio da Economia R$ (prévia 3D)</title>
<style>
  :root{
    --fundo:#101a14; --painel:#0c1410f2; --borda:#2c4434;
    --verde:#3fae6a; --verde-claro:#8fd9a8; --bege:#e8dcb8; --dourado:#d9b45c;
    --texto:#dfe8df; --sub:#9db5a2; --perigo:#d97b6c;
  }
  *{box-sizing:border-box; margin:0}
  html,body{height:100%}
  body{
    font-family:"Segoe UI",system-ui,sans-serif; background:var(--fundo);
    color:var(--texto); overflow:hidden;
  }
  #cena{position:fixed; inset:0; display:block; cursor:grab}
  #cena.arrastando{cursor:grabbing}

  /* ---------- barra de peças ---------- */
  #barra{
    position:fixed; top:14px; left:50%; transform:translateX(-50%);
    display:flex; gap:8px; padding:10px 14px; z-index:10;
    background:var(--painel); border:1px solid var(--borda); border-radius:14px;
    backdrop-filter:blur(6px); box-shadow:0 8px 30px #0009;
  }
  .peca{
    width:64px; height:44px; border:2px solid #ffffff22; border-radius:6px;
    background-size:cover; background-position:center; cursor:pointer;
    position:relative; transition:transform .12s, border-color .12s, box-shadow .12s;
    image-rendering:auto;
  }
  .peca:hover{transform:translateY(-3px) scale(1.05); border-color:#fff6}
  .peca.ativa{
    border-color:var(--dourado); box-shadow:0 0 0 2px #d9b45c66, 0 4px 14px #0008;
    transform:translateY(-3px);
  }
  .peca .v{
    position:absolute; bottom:-7px; left:50%; transform:translateX(-50%);
    font-size:10px; font-weight:700; color:#0c1410; background:var(--dourado);
    padding:1px 6px; border-radius:8px; letter-spacing:.4px;
  }

  /* ---------- ficha da peça ---------- */
  #ficha{
    position:fixed; top:80px; left:16px; z-index:10; width:264px;
    background:var(--painel); border:1px solid var(--borda); border-radius:12px;
    padding:14px 16px; backdrop-filter:blur(6px); box-shadow:0 8px 30px #0009;
  }
  #ficha h1{font-size:17px; color:var(--verde-claro); letter-spacing:.3px}
  #ficha .sub{font-size:12px; color:var(--sub); margin-top:2px}
  #ficha dl{margin-top:10px; font-size:12px; display:grid;
    grid-template-columns:auto 1fr; gap:3px 10px}
  #ficha dt{color:var(--sub)}
  #ficha dd{text-align:right; font-variant-numeric:tabular-nums}
  #ficha .valor{color:var(--dourado); font-weight:700; font-size:13px}

  /* ---------- controles ---------- */
  #controles{
    position:fixed; bottom:14px; left:50%; transform:translateX(-50%);
    display:flex; gap:8px; z-index:10; flex-wrap:wrap; justify-content:center;
    padding:10px 12px; background:var(--painel);
    border:1px solid var(--borda); border-radius:14px; backdrop-filter:blur(6px);
  }
  button{
    font-family:inherit; font-size:12.5px; padding:7px 13px; cursor:pointer;
    background:#16241c; color:var(--texto); border:1px solid var(--borda);
    border-radius:8px; transition:background .12s, border-color .12s;
  }
  button:hover{background:#1d3024; border-color:var(--verde)}
  button.ligado{background:#274a35; border-color:var(--verde); color:#fff}
  #ajuda{
    position:fixed; bottom:74px; right:16px; z-index:10; font-size:11.5px;
    color:var(--sub); background:var(--painel); border:1px solid var(--borda);
    border-radius:10px; padding:8px 12px; text-align:right; line-height:1.5;
  }
  #aviso{
    position:fixed; top:80px; right:16px; z-index:10; width:230px;
    font-size:11.5px; color:var(--sub); background:var(--painel);
    border:1px solid var(--borda); border-radius:10px; padding:9px 12px;
    line-height:1.55;
  }
  #aviso b{color:var(--verde-claro)}
  #aviso .fake{color:var(--perigo); font-weight:600}
  #carregando{
    position:fixed; inset:0; display:flex; align-items:center; justify-content:center;
    z-index:20; background:var(--fundo); font-size:14px; color:var(--sub);
    transition:opacity .4s; letter-spacing:1px;
  }
</style>
</head>
<body>
<div id="carregando">carregando o estúdio da economia…</div>
<canvas id="cena"></canvas>

<nav id="barra"></nav>

<aside id="ficha">
  <h1 id="f-nome">—</h1>
  <div class="sub" id="f-legenda">—</div>
  <dl>
    <dt>Valor facial</dt><dd class="valor" id="f-valor">—</dd>
    <dt>Tipo</dt><dd id="f-tipo">—</dd>
    <dt>Textura</dt><dd id="f-textura">—</dd>
    <dt>Arquivo</dt><dd id="f-arquivo">—</dd>
  </dl>
</aside>

<aside id="aviso">
  <b>Estúdio da Economia R$</b> — 9 peças, cada uma vale o que diz.
  Prévia de aprovação: nada disto foi buildado ainda.
</aside>

<div id="ajuda">
  arrastar&nbsp;→ girar<br>
  scroll&nbsp;→ zoom · botão dir. → pan<br>
  duplo clique&nbsp;→ reenquadrar
</div>

<div id="controles">
  <button data-vista="frente">Frente</button>
  <button data-vista="verso">Verso inclinado</button>
  <button data-vista="deitada">Deitada (topo)</button>
  <button data-vista="lateral">Lateral</button>
  <button data-vista="tresquatro">3/4 topo</button>
  <button data-vista="carteira">💳 Carteira (família)</button>
  <button id="b-girar" class="ligado">⟳ girar</button>
</div>

<script>/*__THREE__*/</script>
<script>
"use strict";
/* ============================================================
   Dados embutidos pelo gerador (texturas REAIS em base64)
   ============================================================ */
const TEXTURAS = __TEXTURAS__;
const FICHAS = __FICHAS__;
const POR_ID = {};
TEXTURAS.forEach(t => POR_ID[t.id] = t);
FICHAS.forEach(f => {
  f.tex = POR_ID[f.id];
  if(f.versoId) f.texVerso = POR_ID[f.versoId];
});

/* ============================================================
   Cena, câmera, luzes
   ============================================================ */
const canvas = document.getElementById('cena');
const renderer = new THREE.WebGLRenderer({canvas, antialias:true});
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.05;

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x101a14);
scene.fog = new THREE.Fog(0x101a14, 34, 90);

const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 200);

/* ---------- luzes de estúdio ---------- */
const luzPreenche = new THREE.HemisphereLight(0xdfe8df, 0x1a261e, 1.05);
scene.add(luzPreenche);
const luzFrente = new THREE.DirectionalLight(0xdfe8df, 0.5);
luzFrente.position.set(0, 4, 14);
scene.add(luzFrente);
const luzChave = new THREE.DirectionalLight(0xfff2dc, 2.0);
luzChave.position.set(7, 12, 8);
luzChave.castShadow = true;
luzChave.shadow.mapSize.set(2048, 2048);
luzChave.shadow.camera.left = -16; luzChave.shadow.camera.right = 16;
luzChave.shadow.camera.top = 16;  luzChave.shadow.camera.bottom = -16;
luzChave.shadow.bias = -0.0004;
scene.add(luzChave);
const luzRecorte = new THREE.DirectionalLight(0xbcd4ff, 0.7);
luzRecorte.position.set(-9, 6, -7);
scene.add(luzRecorte);

/* ---------- environment map procedural (reflexos pro metal) ----------
   Luzes direcionais sozinhas deixam metal escuro: falta IBL. Monta
   um 'estúdio' de painéis emissores e queima via PMREM — o MeshStan-
   dardMaterial pega scene.environment sozinho. */
const envScene = new THREE.Scene();
envScene.background = new THREE.Color(0x0c1410);
function painelEnv(w, h, cor, int, x, y, z, rx, ry){
  const p = new THREE.Mesh(new THREE.PlaneGeometry(w, h),
    new THREE.MeshBasicMaterial({color: cor}));
  p.material.color.multiplyScalar(int);
  p.position.set(x, y, z);
  p.rotation.set(rx || 0, ry || 0, 0);
  envScene.add(p);
}
painelEnv(18, 8, 0xfff4e0, 6.0,  0, 12,   0, Math.PI/2, 0);   // softbox de cima
painelEnv(10, 6, 0xdfe8ff, 3.0, -14,  6,   8, 0, Math.PI/3);  // preench. frio
painelEnv( 8, 5, 0xfff0d8, 3.0,  13,  5,   6, 0, -Math.PI/3); // chave quente
painelEnv(20, 4, 0xbcd4ff, 1.5,   0,  4, -14, 0, Math.PI);    // recorte atrás
const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(envScene, 0.04).texture;
pmrem.dispose();

/* ---------- piso + grade discreta ---------- */
const piso = new THREE.Mesh(
  new THREE.CircleGeometry(30, 64),
  new THREE.MeshStandardMaterial({color:0x16241c, roughness:0.96, metalness:0.0})
);
piso.rotation.x = -Math.PI/2;
piso.receiveShadow = true;
scene.add(piso);
const grade = new THREE.GridHelper(30, 30, 0x2c4434, 0x1c2e22);
grade.position.y = 0.002;
grade.material.transparent = true; grade.material.opacity = 0.35;
scene.add(grade);

/* ============================================================
   Câmera orbital escrita à mão (girar / zoom / pan)
   ============================================================ */
const orbita = {
  alvo: new THREE.Vector3(0, 1.0, 0),
  esferica: new THREE.Spherical(9, Math.PI/2.5, Math.PI/5),
  vaiPara: null,
};
function aplicaCamera(){
  const e = orbita.esferica;
  camera.position.setFromSpherical(e).add(orbita.alvo);
  camera.lookAt(orbita.alvo);
}
let arrastando = false, pan = false, px = 0, py = 0, movido = false;
canvas.addEventListener('pointerdown', ev => {
  arrastando = true; pan = (ev.button === 2);
  px = ev.clientX; py = ev.clientY; movido = false;
  canvas.classList.add('arrastando');
  canvas.setPointerCapture(ev.pointerId);
});
canvas.addEventListener('pointermove', ev => {
  if(!arrastando) return;
  const dx = ev.clientX - px, dy = ev.clientY - py;
  if(Math.abs(dx)+Math.abs(dy) > 2) movido = true;
  px = ev.clientX; py = ev.clientY;
  if(pan){
    const direita = new THREE.Vector3().setFromSpherical(orbita.esferica)
      .cross(camera.up).normalize();
    const cima = new THREE.Vector3().crossVectors(camera.up, direita);
    orbita.alvo.addScaledVector(direita, -dx * orbita.esferica.radius * 0.0011);
    orbita.alvo.addScaledVector(cima, dy * orbita.esferica.radius * 0.0011);
  } else {
    orbita.esferica.theta -= dx * 0.0062;
    orbita.esferica.phi = Math.min(Math.PI-0.08, Math.max(0.12,
      orbita.esferica.phi - dy * 0.0058));
  }
  orbita.vaiPara = null;
});
addEventListener('pointerup', () => {
  arrastando = false; canvas.classList.remove('arrastando');
});
canvas.addEventListener('wheel', ev => {
  ev.preventDefault();
  orbita.esferica.radius = Math.min(40, Math.max(2.2,
    orbita.esferica.radius * (1 + Math.sign(ev.deltaY) * 0.11)));
  orbita.vaiPara = null;
}, {passive:false});
canvas.addEventListener('contextmenu', ev => ev.preventDefault());
canvas.addEventListener('dblclick', () => enquadrar(pecaAtiva));
addEventListener('resize', redimensiona);

/* interpolação suave pra vistas */
function vaiParaVista(radius, phi, theta, alvoY){
  orbita.vaiPara = {radius, phi, theta,
    alvo: new THREE.Vector3(0, alvoY === undefined ? 1.0 : alvoY, 0)};
}
function tickCamera(){
  if(orbita.vaiPara){
    const v = orbita.vaiPara, k = 0.14;
    orbita.esferica.radius += (v.radius - orbita.esferica.radius) * k;
    orbita.esferica.phi    += (v.phi    - orbita.esferica.phi)    * k;
    let d = v.theta - orbita.esferica.theta;
    d = Math.atan2(Math.sin(d), Math.cos(d));
    orbita.esferica.theta += d * k;
    orbita.alvo.lerp(v.alvo, k);
    if(Math.abs(d) < 0.002 && Math.abs(v.radius-orbita.esferica.radius) < 0.02)
      orbita.vaiPara = null;
  }
  aplicaCamera();
}

/* ============================================================
   As peças 3D — nota = folha fina 2.37:1, moeda = cilindro
   ============================================================ */
function carregaTex(info){
  const img = new Image();
  img.src = 'data:image/png;base64,' + info.data;
  const tex = new THREE.Texture(img);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = renderer.capabilities.getMaxAnisotropy();
  img.onload = () => tex.needsUpdate = true;
  return tex;
}

/* Nota: BoxGeometry fina; a cédula 1024×432 nas faces grandes,
   cor do papel nas bordas finas. */
function constroiNota(ficha){
  const W = 2.37, Hh = 1.0, T = 0.012;      // proporção real da cédula
  const tex = carregaTex(ficha.tex);
  const matFrente = new THREE.MeshStandardMaterial({
    map: tex, roughness: 0.62, metalness: 0.06});
  /* v1.2.61: VERSO próprio quando existe ({id}_verso.png); sem verso,
     repete a frente (moeda de 1). */
  const matVerso = ficha.texVerso
    ? new THREE.MeshStandardMaterial({
        map: carregaTex(ficha.texVerso), roughness: 0.62, metalness: 0.06})
    : matFrente;
  const cor = new THREE.Color(0xdad2c2);
  const matBorda = new THREE.MeshStandardMaterial({color:cor, roughness:0.8});
  /* Ordem de materiais do BoxGeometry: [+x, −x, +y, −y, +z, −z].
     A cédula é larga (x) e alta (y) com a espessura em z — os ROSTOS
     grandes são FRENTE (+z) e VERSO (−z). A face −z já mapeia a
     textura legível pra quem olha de FORA da nota (como cédula real:
     cada face é gravada pra ser lida do seu próprio lado) — sem
     espelhamento manual. */
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(W, Hh, T),
    [matBorda, matBorda, matBorda, matBorda, matFrente, matVerso]);
  mesh.castShadow = true; mesh.receiveShadow = true;
  return mesh;
}

/* Moeda: cilindro EM PÉ (face pro espectador, leve inclinação pra trás)
   dentro de um grupo — o giro do palco roda o grupo e a moeda mostra
   frente e verso como moeda de vitrine. */
function constroiMoeda(ficha){
  const R = 0.62, A = 0.09;
  /* clone: a rotação de UV da moeda não pode vazar pra textura das
     notas (ambas nascem da mesma base64 via carregaTex). */
  const tex = carregaTex(ficha.tex).clone();
  tex.needsUpdate = true;
  /* A tampa do cilindro mapeia a textura com o 'pra cima' virado — e a
     moeda é girada pra ficar em pé. Compensa com rotação de UV. */
  tex.center.set(0.5, 0.5);
  tex.rotation = Math.PI/2;   // tampa do cilindro vem girada 90°
  const matFace = new THREE.MeshStandardMaterial({
    map: tex, roughness: 0.5, metalness: 0.2, envMapIntensity: 1.5});
  const matBorda = new THREE.MeshStandardMaterial({
    color: 0x9aa0a8, roughness: 0.45, metalness: 0.6, envMapIntensity: 1.2});
  const cyl = new THREE.Mesh(new THREE.CylinderGeometry(R, R, A, 96),
    [matBorda, matFace, matFace]);
  cyl.castShadow = true; cyl.receiveShadow = true;
  /* face da moeda aponta pra +Z (mesmo lado da nota) — tampa de cima
     vira pro espectador com leve inclinação pra trás */
  cyl.rotation.x = Math.PI/2 - 0.14;
  const grupo = new THREE.Group();
  grupo.add(cyl);
  return grupo;
}

function constroiPeca(ficha){
  return ficha.tipo === 'moeda' ? constroiMoeda(ficha) : constroiNota(ficha);
}

/* ============================================================
   Palco individual (peça ativa) + carteira (família em leque)
   ============================================================ */
let grupoPeca = null, modeloPeca = null, pecaAtiva = FICHAS[6];
const carteira = new THREE.Group(); carteira.visible = false;
scene.add(carteira);

function limpaGrupo(g){
  g.traverse(o => {
    if(o.geometry) o.geometry.dispose();
    if(o.material){ (Array.isArray(o.material)?o.material:[o.material])
      .forEach(m => { if(m.map) m.map.dispose(); m.dispose(); }); }
  });
  scene.remove(g);
}

function mostraPeca(ficha){
  if(grupoPeca) limpaGrupo(grupoPeca);
  carteira.visible = false;
  grupoPeca = new THREE.Group();
  modeloPeca = constroiPeca(ficha);
  modeloPeca.position.y = 1.0;
  modeloPeca.rotation.y = 0;      // abre de FRENTE pro espectador
  grupoPeca.add(modeloPeca);
  // pedestal sutil
  const pedestal = new THREE.Mesh(
    new THREE.CylinderGeometry(1.5, 1.6, 0.12, 64),
    new THREE.MeshStandardMaterial({color:0x0f1a13, roughness:0.9}));
  pedestal.position.y = 0.06;
  pedestal.receiveShadow = true;
  grupoPeca.add(pedestal);
  scene.add(grupoPeca);
  pecaAtiva = ficha;
  fichaUI(ficha);
  marcarAtiva(ficha.id);
  orbita.alvo.set(0, 1.0, 0);
  vaiParaVista(6.2, Math.PI/2 - 0.25, 0.45, 1.0);   // 3/4 frontal: vê a face +Z de leve por cima
}

/* Carteira: as 8 em leque no chão, moeda no meio */
function constroiCarteira(){
  FICHAS.forEach((ficha, i) => {
    const peca = constroiPeca(ficha);
    const ang = (i - (FICHAS.length-1)/2) * 0.30;
    const raio = 4.6;
    /* leque deitado na mesa: pivô ALTO o bastante pra nenhuma ponta
       afundar na placa (dip = 1.185·sin(0.30) ≈ 0.35) */
    peca.position.set(Math.sin(ang)*raio, 0.42,
                      Math.cos(ang)*raio*0.55 - 1.2);
    if(ficha.tipo === 'moeda'){
      /* inclina a moeda de pé pra trás: a FACE fica voltada pra cima,
         visível da câmera alta (de pé reto só se veria o fio) */
      peca.rotation.set(-0.95, 0, -ang*0.6);
    } else {
      peca.rotation.set(-Math.PI/2 + 0.30, 0, -ang*0.6);
    }
    peca.castShadow = true; peca.receiveShadow = true;
    peca.userData.ficha = ficha;
    carteira.add(peca);
  });
  const placa = new THREE.Mesh(
    new THREE.BoxGeometry(9.6, 0.06, 6.2),
    new THREE.MeshStandardMaterial({color:0x0d1610, roughness:0.95}));
  placa.position.set(0, 0.03, -1.2);
  placa.receiveShadow = true;
  carteira.add(placa);
}
constroiCarteira();

function mostraCarteira(){
  if(grupoPeca) limpaGrupo(grupoPeca), grupoPeca = null, modeloPeca = null;
  carteira.visible = true;
  document.querySelectorAll('.peca').forEach(p => p.classList.remove('ativa'));
  fichaUI({nome:'A carteira — família R$ completa',
    legenda:'moeda de 1 + cédulas de 2 a 200, cada uma vale o que diz',
    valor:'R$ 888', tipo:'9 peças', textura:'9 frentes + 8 versos (todas em HD)',
    arquivo:'tools/gen_dinheiro.py'});
  orbita.alvo.set(0, 0.35, -0.3);
  vaiParaVista(12.0, 0.82, 0.32, 0.5);   // de cima, leque centrado no quadro
}

/* pick na carteira: clique numa peça troca pra ela */
let downX = 0, downY = 0;
canvas.addEventListener('pointerdown', ev => { downX = ev.clientX; downY = ev.clientY; });
canvas.addEventListener('pointerup', ev => {
  if(movido || Math.abs(ev.clientX-downX)+Math.abs(ev.clientY-downY) > 4) return;
  if(!carteira.visible) return;
  const r = new THREE.Raycaster();
  r.setFromCamera(new THREE.Vector2(
    (ev.clientX/innerWidth)*2-1, -(ev.clientY/innerHeight)*2+1), camera);
  const hit = r.intersectObjects(carteira.children, true)[0];
  if(hit && hit.object.userData.ficha) mostraPeca(hit.object.userData.ficha);
});

/* ============================================================
   UI
   ============================================================ */
const barra = document.getElementById('barra');
FICHAS.forEach(f => {
  const b = document.createElement('button');
  b.className = 'peca'; b.title = f.nome;
  b.style.backgroundImage = 'url(data:image/png;base64,' + f.tex.data + ')';
  b.innerHTML = '<span class="v">' + f.valor + '</span>';
  b.onclick = () => mostraPeca(f);
  b.dataset.id = f.id;
  barra.appendChild(b);
});
const bCarteira = document.createElement('button');
bCarteira.className = 'peca'; bCarteira.title = 'A carteira (família toda)';
bCarteira.style.background = 'linear-gradient(135deg,#274a35,#0d1610)';
bCarteira.style.display = 'flex'; bCarteira.style.alignItems = 'center';
bCarteira.style.justifyContent = 'center';
bCarteira.textContent = '💳'; bCarteira.style.fontSize = '22px';
bCarteira.onclick = mostraCarteira;
barra.appendChild(bCarteira);

function marcarAtiva(id){
  document.querySelectorAll('.peca').forEach(p =>
    p.classList.toggle('ativa', p.dataset.id === id));
}
function fichaUI(f){
  document.getElementById('f-nome').textContent = f.nome;
  document.getElementById('f-legenda').textContent = f.legenda || '';
  document.getElementById('f-valor').textContent = f.valor;
  document.getElementById('f-tipo').textContent = f.tipo || '';
  document.getElementById('f-textura').textContent = f.textura || '';
  document.getElementById('f-arquivo').textContent = f.arquivo || '';
}

/* ---------- vistas ---------- */
/* Cada vista também REALINHA a rotação da peça (alvo de rotation.y
   interpolado no tick — sem isso a peça abre de perfil quando o
   auto-giro tinha parado num ângulo qualquer). */
let rotYAlvo = null;
/* as vistas se resolvem pela POSIÇÃO da câmera — a peça volta sempre
   pro yaw 0 (convenção: az a partir de +Z, polar a partir de +Y) */
const ROT_VISTAS = {
  frente: 0, verso: 0, deitada: 0, lateral: 0, tresquatro: 0,
};
document.querySelectorAll('[data-vista]').forEach(b => {
  b.onclick = () => {
    document.querySelectorAll('#controles button').forEach(x => x.classList.remove('ligado'));
    if(b.id === 'b-girar') b.classList.add('ligado');
    rotYAlvo = ROT_VISTAS[b.dataset.vista] ?? null;
    switch(b.dataset.vista){
      /* assinatura: (dist, polar-do-zênite, azimute-do-+Z) */
      case 'frente':    vaiParaVista(5.4, Math.PI/2 - 0.02, 0.06); break;
      case 'verso':     vaiParaVista(5.8, Math.PI/2 - 0.25, Math.PI - 0.35); break;
      case 'deitada':   vaiParaVista(6.4, 0.16, 0.35); break;
      case 'lateral':   vaiParaVista(4.6, Math.PI/2 - 0.02, Math.PI/2); break;
      case 'tresquatro':vaiParaVista(6.6, Math.PI/2 - 0.55, 0.62); break;
      case 'carteira':  mostraCarteira(); return;   // a família inteira
    }
  };
});

/* auto-girar suave (começa LIGADO devagar) */
let girando = true;
const bGirar = document.getElementById('b-girar');
bGirar.onclick = () => {
  girando = !girando;
  bGirar.classList.toggle('ligado', girando);
};
canvas.addEventListener('pointerdown', () => { girando = false; bGirar.classList.remove('ligado'); rotYAlvo = null; });

function redimensiona(){
  renderer.setSize(innerWidth, innerHeight);
  camera.aspect = innerWidth/innerHeight;
  camera.updateProjectionMatrix();
}
redimensiona();

/* ============================================================
   Loop
   ============================================================ */
const relogio = new THREE.Clock();
function tick(){
  requestAnimationFrame(tick);
  const dt = relogio.getDelta();
  if(girando){
    if(modeloPeca) modeloPeca.rotation.y += dt * 0.35;
    if(carteira.visible) carteira.rotation.y += dt * 0.05;
  } else if(rotYAlvo !== null && modeloPeca){
    // interpola a rotação da peça até o alvo da vista escolhida
    let d = rotYAlvo - modeloPeca.rotation.y;
    d = Math.atan2(Math.sin(d), Math.cos(d));
    modeloPeca.rotation.y += d * Math.min(1, dt * 7);
    if(Math.abs(d) < 0.01) rotYAlvo = null;
  }
  tickCamera();
  renderer.render(scene, camera);
}

/* abre na nota de 100 (a cara da família) */
mostraPeca(FICHAS[6]);
document.getElementById('carregando').style.opacity = '0';
setTimeout(() => document.getElementById('carregando').remove(), 450);
tick();
</script>
</body>
</html>
"""


def main():
    if not os.path.exists(VENDOR):
        raise SystemExit("three.min.js ausente em preview/vendor — rode o "
                         "gerador do estúdio de armas primeiro (ele baixa).")
    with open(VENDOR, "r", encoding="utf-8") as f:
        three_js = f.read()

    # frente de todas + VERSO de cada cédula que tiver {id}_verso.png
    dados, fichas = [], []
    for p in PECAS:
        frente = embute(p[0])
        dados.append(frente)
        ficha = {"id": p[0], "nome": p[1], "valor": p[2], "tipo": p[3],
                 "legenda": p[4],
                 "textura": f"{frente['w']}×{frente['h']}",
                 "arquivo": p[0] + ".png"}
        verso = os.path.join(TEX, p[0] + "_verso.png")
        if os.path.exists(verso):
            dados.append(embute(p[0] + "_verso"))
            ficha["versoId"] = p[0] + "_verso"
            ficha["arquivo"] += f" + {p[0]}_verso.png"
        fichas.append(ficha)
    dados_json = json.dumps(dados)
    fichas_json = json.dumps(fichas, ensure_ascii=False)

    html = HTML.replace("/*__THREE__*/", three_js)
    html = html.replace("__TEXTURAS__", dados_json)
    html = html.replace("__FICHAS__", fichas_json)

    with open(SAIDA, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Prévia 3D gerada: {os.path.relpath(SAIDA, RAIZ)} "
          f"({os.path.getsize(SAIDA) // 1024} KB)")


if __name__ == "__main__":
    main()
