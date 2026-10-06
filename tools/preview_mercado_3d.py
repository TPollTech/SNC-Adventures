#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""preview_mercado_3d.py — PREVIA 3D NAVEGAVEL do Mercado Equinao (v1.2.78).

Gera `preview/mercado-3d.html`: estudio Three.js AUTOCONTIDO (offline —
three.min.js embutido) que carrega o salao inteiro direto das ROWS do
gerador (`tools/gen_mercado.py`, a MESMA fonte do structure/mercado_gago.nbt)
e deixa dar uma volta nele: girar, dar zoom, andar com o pan e clicar num
bloco pra ficha (id/coords). As tres peles regionais (classico/sertao/serra)
sao o mesmo salao com paleta diferente — botao pra trocar sem recarregar.

As cores NAO sao chute: as texturas REAIS do mod (textures/block/*.png) sao
amostradas com PIL e os blocos vanilla ganham a cor media conhecida.

Uso: python tools/preview_mercado_3d.py  (a partir da raiz do mod)
Saida: preview/mercado-3d.html (abrir na aba Preview do Freebuff)
"""
import json
import math
import os
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(RAIZ, "tools"))

import gen_mercado as gerador  # noqa: E402  (o gerador e a fonte da verdade)

VENDOR = os.path.join(RAIZ, "preview", "vendor", "three.min.js")
SAIDA = os.path.join(RAIZ, "preview", "mercado-3d.html")
TEX = os.path.join(RAIZ, "src", "main", "resources", "assets", "intoxicantes",
                   "textures", "block")

# cor media de cada bloco vanilla que o mercado usa (aproximacao de textura)
CORES_VANILLA = {
    "minecraft:white_concrete": "#cfd4d5",
    "minecraft:green_concrete": "#4c6b4f",
    "minecraft:smooth_quartz": "#dcd8cb",
    "minecraft:gray_concrete": "#4c4c4c",
    "minecraft:blue_concrete": "#3b5fa8",
    "minecraft:barrel": "#96703f",
    "minecraft:lantern": "#c99347",
    "minecraft:black_stained_glass_pane": "#141414",
    "minecraft:black_stained_glass": "#141414",
    "minecraft:light_blue_stained_glass": "#a8d4ef",
    "minecraft:oak_wall_sign": "#a07a45",
    "minecraft:shroomlight": "#f2a33c",
    "minecraft:oak_slab": "#a07a45",
    "minecraft:smooth_stone_slab": "#9a9a95",
    "minecraft:smooth_sandstone_slab": "#d9cd9c",
    "minecraft:smooth_sandstone": "#d9cd9c",
    "minecraft:orange_terracotta": "#c07a45",
    "minecraft:cut_sandstone": "#d6ca9a",
    "minecraft:light_gray_concrete": "#8e8e8b",
    "minecraft:gray_terracotta": "#6b625b",
    "minecraft:stone_bricks": "#7f8580",
    "minecraft:spruce_planks": "#7a5c3a",
    "minecraft:smooth_stone": "#a2a29d",
    "minecraft:polished_deepslate": "#4b4b50",
}

# textura REAL do mod que amostra a cor de cada bloco proprio
TEXTURA_MOD = {
    "intoxicantes:asfalto": "asfalto.png",
    "intoxicantes:poste_luz": "poste_luz_on.png",
    "intoxicantes:faixa_pedestre": "faixa_pedestre.png",
    "intoxicantes:hidrante": "hidrante.png",
    "intoxicantes:porta_grade": "porta_grade.png",
    "intoxicantes:caixa_mercado": "caixa_mercado.png",
    "intoxicantes:lampada_led": "lampada_led.png",
    "intoxicantes:placa_esquinao": "placa_esquinao_coluna.png",
    "intoxicantes:painel_led": "placa_esquinao_tela.png",
    "intoxicantes:prateleira_mercado": "prateleira_mercado.png",
}

# cor usada quando a textura nao existe no jar
# v1.2.78 — peça COM FRENTE (bebedouro/lavadora): o giro em Y que a prévia
# aplica em torno do centro do bloco. Sinal do three.js (Y para cima): a frente
# base do modelo é -Z, então "east" = -90 graus.
GIROS = {"north": 0, "east": -90, "south": 180, "west": 90}

# v1.2.78 — a mobília nova (engradado/bebedouro/lavadora) é bloco do mod e
# ainda não tem PNG no disco enquanto a prévia roda: a textura é pintada em
# MEMÓRIA pelo próprio gerador canônico, então a prévia mostra o material
# certo sem gravar recurso nenhum no jogo.
MOBILIA_EM_MEMORIA = {}
# qual textura manda na cor vista de longe de cada peça (o corpo)
MOBILIA_TEXTURA = {
    "intoxicantes:engradado_mercado": "engradado_corpo",
    "intoxicantes:bebedouro_mercado": "bebedouro_armario",
    "intoxicantes:bebedouro_garrafao": "garrafao_pet",
    "intoxicantes:lavadora_mercado": "lavadora_corpo",
}


def _mobilia_em_memoria():
    if MOBILIA_EM_MEMORIA:
        return MOBILIA_EM_MEMORIA
    try:
        import gen_mobilia_mercado
    except ImportError:
        return {}
    for peca in gen_mobilia_mercado.PECAS:
        for nome, img in peca['tex']().items():
            MOBILIA_EM_MEMORIA[nome] = img
    return MOBILIA_EM_MEMORIA


COR_FALLBACK = {
    "intoxicantes:asfalto": "#2b2d30",
    "intoxicantes:poste_luz": "#f3e7b8",
    "intoxicantes:faixa_pedestre": "#e8e6df",
    "intoxicantes:hidrante": "#b3352b",
    "intoxicantes:porta_grade": "#3c4450",
    "intoxicantes:caixa_mercado": "#8d3f36",
    "intoxicantes:lampada_led": "#fff8e0",
    "intoxicantes:placa_esquinao": "#0a0a0a",
    "intoxicantes:painel_led": "#0d2a3a",
    "intoxicantes:prateleira_mercado": "#9aa2a8",
    "intoxicantes:engradado_mercado": "#a5713a",
    "intoxicantes:bebedouro_mercado": "#b0b8bc",
    "intoxicantes:bebedouro_garrafao": "#4d8ac8",
    "intoxicantes:lavadora_mercado": "#e8e8e4",
}

# blocos meio-bloco (a base do balcao) e blocos translucidos
MEIO = {"minecraft:smooth_stone_slab", "minecraft:smooth_sandstone_slab",
        "minecraft:oak_slab"}
VIDRO = {"minecraft:light_blue_stained_glass", "minecraft:black_stained_glass",
         "minecraft:black_stained_glass_pane"}


def geometria(estado, bid):
    """0 = cubo, 1 = meio bloco, 2 = painel fino no Z, 3 = painel fino no X.

    A vitrine da fachada e as geladeiras sao PAINEL de vidro (east/west ligado):
    desenhadas como cubo viram uma parede preta opaca e escondem o salao — que
    e justamente o que a previa precisa mostrar.
    """
    if bid in MEIO:
        return 1
    props = estado.get("properties", {}) or {}
    if bid.endswith("_pane") or bid == "minecraft:oak_wall_sign":
        if props.get("east") == "true" or props.get("west") == "true":
            return 2
        if props.get("north") == "true" or props.get("south") == "true":
            return 3
    return 0


def cor_media(caminho, imagem=None):
    """Cor media de uma textura 16x16 (ignora alpha 0)."""
    from PIL import Image
    if imagem is None:
        with Image.open(caminho) as im:
            return cor_media(None, imagem=im)
    with imagem as im:
        im = im.convert("RGBA").resize((16, 16))
        bruto = im.tobytes()
    px = [bruto[i:i + 4] for i in range(0, len(bruto), 4) if bruto[i + 3] > 40]
    if not px:
        return None
    return "#%02x%02x%02x" % (sum(p[0] for p in px) // len(px),
                              sum(p[1] for p in px) // len(px),
                              sum(p[2] for p in px) // len(px))


def paleta_de_cores():
    """{id: cor} de TODOS os blocos que os 3 templates usam."""
    ids = set()
    for regiao in gerador.REGIOES:
        for estado in gerador.paleta_da_regiao(regiao).values():
            ids.add(estado["id"])
    cores = {}
    memoria = _mobilia_em_memoria()
    for bid in sorted(ids):
        if bid in CORES_VANILLA:
            cores[bid] = CORES_VANILLA[bid]
        elif bid in MOBILIA_TEXTURA and MOBILIA_TEXTURA[bid] in memoria:
            cores[bid] = cor_media(None, imagem=memoria[MOBILIA_TEXTURA[bid]].copy())
        elif bid in TEXTURA_MOD:
            arquivo = os.path.join(TEX, TEXTURA_MOD[bid])
            cores[bid] = cor_media(arquivo) if os.path.isfile(arquivo) else COR_FALLBACK[bid]
        else:
            cores[bid] = "#ff00ff"     # magenta = bloco sem cor conhecida
    return cores


def coleta(regiao, cores):
    """Blocos do template de uma regiao: [x, y, z, cor, meio, giro]."""
    paleta = gerador.paleta_da_regiao(regiao)
    ordem = sorted(cores)                       # indice 0 = 1o id da lista
    onde = {bid: i for i, bid in enumerate(ordem)}
    saida = []
    for y, camada in enumerate(gerador.ROWS):
        for z, linha in enumerate(camada):
            for x, ch in enumerate(linha):
                estado = paleta[ch]
                bid = estado["id"]
                if bid == "minecraft:air":
                    continue
                giro = GIROS.get((estado.get("properties") or {}).get("facing"), 0)
                saida.append([x, y, z, onde[bid], 1 if bid in MEIO else 0, giro])
    return saida


HTML = r"""<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Prévia 3D — Mercado Equinao (v1.2.78)</title>
<style>
  :root{
    --fundo:#0f1114; --painel:rgba(18,21,25,.93); --borda:#2b3138;
    --texto:#dfe4ea; --sub:#98a1ab; --destaque:#7fd1a3; --aviso:#e8b06a;
  }
  *{box-sizing:border-box}
  html,body{margin:0;height:100%;overflow:hidden;background:var(--fundo);
    color:var(--texto);font:13px/1.45 "Segoe UI",Arial,sans-serif}
  #cena{position:fixed;inset:0;display:block}
  aside{position:fixed;background:var(--painel);border:1px solid var(--borda);
    border-radius:10px;padding:12px 13px;backdrop-filter:blur(6px)}
  #painel{top:14px;left:14px;width:250px}
  #painel h1{font-size:14px;margin:0 0 2px;color:#fff}
  #painel .sub{color:var(--sub);font-size:11.5px;margin-bottom:10px}
  .grupo{margin:11px 0 0}
  .grupo>label{display:block;font-size:10.5px;letter-spacing:1.3px;
    text-transform:uppercase;color:var(--sub);margin-bottom:6px}
  .botoes{display:flex;flex-wrap:wrap;gap:5px}
  button{background:#1c2127;color:var(--texto);border:1px solid var(--borda);
    border-radius:6px;padding:5px 9px;font:inherit;font-size:12px;cursor:pointer}
  button:hover{background:#242b33;border-color:#3c454f}
  button.ligado{background:#1d3b30;border-color:var(--destaque);color:var(--destaque)}
  #ficha{right:14px;top:14px;width:262px}
  #ficha h2{font-size:12.5px;margin:0 0 6px;color:#fff}
  #ficha code{font-family:Consolas,monospace;font-size:11.5px;color:var(--destaque)}
  #ficha dl{display:grid;grid-template-columns:62px 1fr;gap:3px 8px;margin:8px 0 0}
  #ficha dt{color:var(--sub);font-size:11px}
  #ficha dd{margin:0;font-size:11.5px}
  #contagem{color:var(--aviso);font-size:11.5px;margin-top:9px}
  #ajuda{left:14px;bottom:14px;font-size:11.5px;color:var(--sub)}
  #legenda{right:14px;bottom:14px;width:262px;max-height:44vh;overflow:auto}
  #legenda .linha{display:flex;align-items:center;gap:7px;font-size:11.5px;
    padding:2px 0;color:var(--sub)}
  #legenda .chip{width:12px;height:12px;border-radius:3px;flex:0 0 auto;
    border:1px solid rgba(255,255,255,.16)}
  #dica{position:fixed;pointer-events:none;background:rgba(10,12,15,.95);
    border:1px solid var(--borda);border-radius:6px;padding:5px 8px;font-size:11.5px;
    display:none;z-index:9;max-width:260px}
  #carregando{position:fixed;inset:0;display:flex;align-items:center;
    justify-content:center;background:var(--fundo);z-index:20;color:var(--sub);
    letter-spacing:1px}
</style>
</head>
<body>
<div id="carregando">carregando o mercado…</div>
<canvas id="cena"></canvas>

<aside id="painel">
  <h1>Mercado Equinao — salao 3D</h1>
  <div class="sub">direto das ROWS de <code>gen_mercado.py</code> (o mesmo
    template do mundo)</div>
  <div class="grupo"><label>Pele da regiao</label>
    <div class="botoes" id="regioes"></div></div>
  <div class="grupo"><label>Camadas</label>
    <div class="botoes" id="camadas"></div></div>
  <div class="grupo"><label>Vistas</label>
    <div class="botoes" id="vistas"></div></div>
</aside>

<aside id="ficha">
  <h2 id="f-titulo">—</h2>
  <div id="f-sub" class="sub">clique num bloco</div>
  <dl>
    <dt>id</dt><dd><code id="f-id">—</code></dd>
    <dt>pos</dt><dd id="f-pos">—</dd>
    <dt>char</dt><dd id="f-char">—</dd>
  </dl>
  <div id="contagem"></div>
</aside>

<aside id="legenda"></aside>
<div id="dica"></div>
<div id="ajuda">
  arrastar <b>esquerdo</b> → girar · <b>scroll</b> → zoom · <b>direito</b>/shift → pan ·
  clique num bloco → ficha · duplo clique → reenquadrar
</div>

<script>/*__THREE__*/</script>
<script>
"use strict";
/* ============================================================
   Dados embutidos pelo gerador (ROWS do gen_mercado.py)
   ============================================================ */
const DADOS = __DADOS__;
const ROTULO_CAMADAS = __CAMADAS__;
const VISTAS = __VISTAS__;
/** ABERTA NO SALAO (sem teto/fachada): e o que interessa aprovar nesta versao. */
const VISTA_INICIAL = __INICIAL__;
let semTextura=false;
__MODEL_RENDERER__

const canvas = document.getElementById("cena");
// preserveDrawingBuffer: o print da aba Preview precisa pegar o frame; sem
// isso o buffer é apagado depois do composite e a captura sai em branco
const renderer = new THREE.WebGLRenderer({canvas, antialias:true,
    preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.outputColorSpace = THREE.SRGBColorSpace;
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x0f1114);
scene.fog = new THREE.Fog(0x0f1114, 55, 150);

const camera = new THREE.PerspectiveCamera(45, 1, .1, 400);
scene.add(new THREE.HemisphereLight(0xdfe8f2, 0x2a2f36, 1.15));
const sol = new THREE.DirectionalLight(0xfff3df, 1.35);
sol.position.set(26, 44, 20);
scene.add(sol);
const preenchimento = new THREE.DirectionalLight(0x9fb6d8, .45);
preenchimento.position.set(-20, 18, -14);
scene.add(preenchimento);
const chao = new THREE.Mesh(new THREE.PlaneGeometry(220, 220),
  new THREE.MeshLambertMaterial({color:0x16191d}));
chao.rotation.x = -Math.PI/2;
chao.position.y = -1.02;
scene.add(chao);
const grade = new THREE.GridHelper(120, 60, 0x2a3138, 0x1b2026);
grade.position.y = -1;
grade.material.transparent = true; grade.material.opacity = .5;
scene.add(grade);

/* ---------- geometrias ---------- */
const GEOMETRIAS = [
  new THREE.BoxGeometry(1, 1, 1),        // 0 = cubo
  new THREE.BoxGeometry(1, .5, 1),       // 1 = meio bloco (balcao)
  new THREE.BoxGeometry(1, 1, .12),      // 2 = painel fino no Z (vitrine/geladeira)
  new THREE.BoxGeometry(.12, 1, 1),      // 3 = painel fino no X
];

/* ---------- camera orbital escrita a mao ---------- */
const orbita = {
  alvo: new THREE.Vector3(13.5, 2.5, 9.5),
  esferica: new THREE.Spherical(44, Math.PI * .42, Math.PI * .28),
  vaiPara: null,
};
function aplicaCamera(){
  const p = new THREE.Vector3().setFromSpherical(orbita.esferica).add(orbita.alvo);
  camera.position.copy(p);
  camera.lookAt(orbita.alvo);
}
function enquadra(v){
  orbita.alvo.set(v.alvo[0], v.alvo[1], v.alvo[2]);
  orbita.esferica.set(v.dist, v.phi, v.theta);
  aplicaCamera();
}

let arrastando = false, ultimoX = 0, ultimoY = 0, botaoPan = false;
canvas.addEventListener("contextmenu", e => e.preventDefault());
canvas.addEventListener("pointerdown", e => {
  arrastando = true; ultimoX = e.clientX; ultimoY = e.clientY;
  botaoPan = e.button === 2 || e.shiftKey;
  canvas.setPointerCapture(e.pointerId);
});
canvas.addEventListener("pointerup", e => {
  arrastando = false; botaoPan = false;
  if (canvas.hasPointerCapture(e.pointerId)) canvas.releasePointerCapture(e.pointerId);
});
canvas.addEventListener("pointermove", e => {
  if (arrastando){
    const dx = e.clientX - ultimoX, dy = e.clientY - ultimoY;
    ultimoX = e.clientX; ultimoY = e.clientY;
    if (botaoPan){
      const escala = orbita.esferica.radius * .0016;
      const dir = new THREE.Vector3();
      camera.getWorldDirection(dir);
      const direita = new THREE.Vector3().crossVectors(dir, camera.up).normalize();
      const cima = new THREE.Vector3().crossVectors(direita, dir).normalize();
      orbita.alvo.addScaledVector(direita, -dx * escala);
      orbita.alvo.addScaledVector(cima, dy * escala);
    } else {
      orbita.esferica.theta -= dx * .006;
      orbita.esferica.phi = Math.max(.08, Math.min(Math.PI - .12,
          orbita.esferica.phi - dy * .006));
    }
    aplicaCamera();
    return;
  }
  realca(e);
});
canvas.addEventListener("wheel", e => {
  e.preventDefault();
  orbita.esferica.radius = Math.max(3, Math.min(150,
      orbita.esferica.radius * (e.deltaY > 0 ? 1.09 : .92)));
  aplicaCamera();
}, {passive:false});
canvas.addEventListener("dblclick", () => enquadra(VISTAS[VISTA_INICIAL].valores));

/* ---------- cena das tres peles ---------- */
let grupo = null, malhas = [], cur = "classico";
const estados = {teto:true, fachada:true, patio:true, gondolas:true, mobilia:true};

/* Uma camada LIGADA aparece: o estado true = visivel. (ligado + y>=4 = o
   telhado some do grupo de instâncias — e vice-versa.) */
function grupoDe(b, variante){
  const [x, y, z] = b;
  if (!estados.teto && y >= 4) return false;
  // as lampadas de teto (y=3) tambem belong ao teto: sem isso elas ficam
  // boiando no meio do salao aberto e confundem a leitura das gôndolas
  if (b[3] === DADOS.lampada && !estados.teto) return false;
  if (!estados.fachada && z >= 17 && z <= 19) return false;
  if (!estados.patio && z >= 20) return false;
  if (b[3] === DADOS.gondola && !estados.gondolas) return false;
  if ((x === 3 || x === 23) && !estados.mobilia) return false;
  return true;
}

function constroi(regiao){
  if (grupo){ scene.remove(grupo); grupo = null; }
  malhas = [];
  grupo = new THREE.Group();
  const variante = DADOS.variantes[regiao];
  const contagens = {};
  for (const b of variante){
    if (!grupoDe(b) || DADOS.modelos[b[3]]) continue;
    const cor = DADOS.cores[b[3]];
    const chave = b[3] + ":" + b[4];
    contagens[chave] = (contagens[chave] || 0) + 1;
  }
  for (const chave in contagens){
    const total = contagens[chave];
    const geo = parseInt(chave.split(":")[1], 10);
    const idx = parseInt(chave.split(":")[0], 10);
    const cor = DADOS.cores[idx];
    const material = new THREE.MeshLambertMaterial({
      color: cor,
      transparent: !!DADOS.vidro[idx],
      opacity: DADOS.vidro[idx] ? .42 : 1,
    });
    const malha = new THREE.InstancedMesh(GEOMETRIAS[geo], material, total);
    malha.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    const blocos = [];
    let i = 0;
    for (const b of variante){
      if (!grupoDe(b)) continue;
      if (b[3] + ":" + b[4] !== chave) continue;
      const y = b[1] + (geo === 1 ? .25 : .5);
      const m = new THREE.Matrix4().makeTranslation(b[0] + .5, y, b[2] + .5);
      if(b[6])m.multiply(new THREE.Matrix4().makeRotationY(b[6]*Math.PI/180));
      malha.setMatrixAt(i++, m);
      blocos.push(b);
    }
    malha.instanceMatrix.needsUpdate = true;
    malha.userData.blocos = blocos;
    malha.userData.geo = geo;
    grupo.add(malha);
    malhas.push(malha);
  }
  // Modelos nativos da fonte, não caixas coloridas fingindo ser mobília.
  for(const b of variante){
    if(!grupoDe(b) || !DADOS.modelos[b[3]])continue;
    const entrada=DADOS.modelos[b[3]];
    const model=entrada.metades ? entrada.metades[b[5]==='u' ? 'upper':'lower'] : entrada.model;
    const local=new THREE.Group();
    for(const el of model.elements)local.add(malhaDo(el,model.textures));
    local.position.set(-.5,0,-.5);
    const raiz=new THREE.Group(); raiz.add(local);
    raiz.position.set(b[0]+.5,b[1],b[2]+.5); raiz.rotation.y=b[6]*Math.PI/180;
    grupo.add(raiz);
  }
  // Verde translúcido = área de circulação, NÃO recurso do jogo.
  for(let i=0;i<DADOS.corredores.length;i++){
    const faixa=DADOS.corredores[i];
    const interno=i>=4 && i<4+4; // corredor central longitudinal
    const faceGondola=i>=8;
    const cor=faceGondola?0xe3b956:0x54bf92;
    const piso=new THREE.Mesh(new THREE.PlaneGeometry(faixa[2],faixa[3]),
      new THREE.MeshBasicMaterial({color:cor,transparent:true,
        opacity:faceGondola?.28:(interno?.18:.12),depthWrite:false}));
    piso.rotation.x=-Math.PI/2;piso.position.set(faixa[0],faceGondola?1.014:1.012,faixa[1]);grupo.add(piso);
  }
  document.getElementById('contagem').textContent='24 gôndolas · 18 slots cada · 4 faces de venda (âmbar) · corredores (verde). Estrutura vanilla simplificada; estoque não desenhado.';
  // o Gago de plantao (entidade do template) — caixa + plaquinha flutuante
  const gago = new THREE.Mesh(new THREE.BoxGeometry(.62, 1.8, .62),
      new THREE.MeshLambertMaterial({color:0xc9a06a}));
  gago.position.set(DADOS.gago[0] + .5, DADOS.gago[1] + .9, DADOS.gago[2] + .5);
  grupo.add(gago);
  scene.add(grupo);
  realca(null);
}

function rebuild(){constroi(cur);}

/* ---------- ficha + realce ---------- */
const raio = new THREE.Raycaster();
const mouse = new THREE.Vector2();
const dica = document.getElementById("dica");
const realceLinha = new THREE.LineSegments(
  new THREE.EdgesGeometry(new THREE.BoxGeometry(1.004, 1.004, 1.004)),
  new THREE.LineBasicMaterial({color:0x7fd1a3}));
realceLinha.visible = false;
scene.add(realceLinha);

function realca(ev){
  if (!grupo || !ev){ realceLinha.visible = false; dica.style.display = "none"; return; }
  const ret = canvas.getBoundingClientRect();
  mouse.x = ((ev.clientX - ret.left) / ret.width) * 2 - 1;
  mouse.y = -((ev.clientY - ret.top) / ret.height) * 2 + 1;
  raio.setFromCamera(mouse, camera);
  const acertos = raio.intersectObjects(malhas, false);
  if (!acertos.length){ realceLinha.visible = false; dica.style.display = "none"; return; }
  const acerto = acertos[0];
  const b = acerto.object.userData.blocos[acerto.instanceId];
  const geo = acerto.object.userData.geo;
  realceLinha.scale.set(geo === 3 ? .12 : 1, geo === 1 ? .5 : 1, geo === 2 ? .12 : 1);
  realceLinha.position.set(b[0] + .5, b[1] + (geo === 1 ? .25 : .5), b[2] + .5);
  realceLinha.visible = true;
  const id = DADOS.ids[b[3]];
  document.getElementById("f-titulo").textContent = id.split(":")[1];
  document.getElementById("f-sub").textContent = DADO_ROTULO[b[3]] || "bloco do salao";
  document.getElementById("f-id").textContent = id;
  document.getElementById("f-pos").textContent = "(" + b[0] + ", " + b[1] + ", " + b[2] + ")";
  document.getElementById("f-char").textContent = b[5] || ".";
  dica.innerHTML = "<b>" + id.split(":")[1] + "</b><br>(" + b[0] + ", " + b[1]
    + ", " + b[2] + ")";
  dica.style.display = "block";
  dica.style.left = Math.min(ev.clientX + 14, innerWidth - 270) + "px";
  dica.style.top = (ev.clientY + 16) + "px";
}
canvas.addEventListener("pointerleave", () => realca(null));

/* ---------- botoes ---------- */
const REGIOES = [["classico", "Classico"], ["sertao", "Sertao"], ["serra", "Serra"]];
function criaBotoes(alvo, itens, aoClicar){
  const caixa = document.getElementById(alvo);
  itens.forEach(([chave, rotulo]) => {
    const b = document.createElement("button");
    b.textContent = rotulo;
    b.dataset.chave = chave;
    b.addEventListener("click", () => aoClicar(chave, b));
    caixa.appendChild(b);
  });
}
function marca(alvo, chave){
  document.querySelectorAll("#" + alvo + " button").forEach(b =>
    b.classList.toggle("ligado", b.dataset.chave === chave));
}

criaBotoes("regioes", REGIOES, (chave, botao) => {
  cur = chave; marca("regioes", chave); constroi(chave);
});
criaBotoes("camadas", Object.keys(ROTULO_CAMADAS).map(
    k => [k, ROTULO_CAMADAS[k]]), (chave, botao) => {
  estados[chave] = !estados[chave];
  botao.classList.toggle("ligado", estados[chave]);
  rebuild();
});
criaBotoes("vistas", VISTAS.map((v, i) => [String(i), v.nome]), (chave) => {
  const vista = VISTAS[parseInt(chave, 10)];
  // cada vista traz as camadas que fazem sentido nela (o salao vem sem teto)
  Object.assign(estados, vista.camadas || {});
  Object.keys(estados).forEach(k => marca("camadas", k));
  rebuild();
  enquadra(vista.valores);
});

/* ---------- legenda ---------- */
(function legenda(){
  const caixa = document.getElementById("legenda");
  const conta = {};
  for (const b of DADOS.variantes.classico) conta[b[3]] = (conta[b[3]] || 0) + 1;
  Object.keys(DADO_ROTULO).forEach(idx => {
    const i = parseInt(idx, 10);
    const linha = document.createElement("div");
    linha.className = "linha";
    linha.innerHTML = '<span class="chip" style="background:' + DADOS.cores[i]
      + '"></span><span>' + DADO_ROTULO[idx] + " · " + conta[i] + "</span>";
    caixa.appendChild(linha);
  });
})();

/* ---------- resize + loop ---------- */
function resize(){
  // a aba Preview pode abrir com viewport 0 e só crescer depois: sem este
  // fallback o canvas fica com width/height = 0 e a cena não aparece
  const w = document.documentElement.clientWidth || innerWidth;
  const h = document.documentElement.clientHeight || innerHeight;
  if (!w || !h) return;
  if (canvas.width === w * renderer.getPixelRatio()
      && canvas.height === h * renderer.getPixelRatio()) return;
  renderer.setSize(w, h, false);
  camera.aspect = w / h;
  camera.updateProjectionMatrix();
}
addEventListener("resize", resize);
if (typeof ResizeObserver !== "undefined")
  new ResizeObserver(resize).observe(document.documentElement);
resize();
requestAnimationFrame(resize);
marca("regioes", "classico");
Object.assign(estados, VISTAS[VISTA_INICIAL].camadas || {});
Object.keys(estados).forEach(k => marca("camadas", k));
(async()=>{
  const tarefas=[];
  for(const entrada of Object.values(DADOS.modelos))
    for(const [ref,url] of Object.entries(entrada.textures))tarefas.push(carrega(ref,url));
  await Promise.all(tarefas); constroi(cur);
  document.getElementById('carregando')?.remove();
})();
enquadra(VISTAS[VISTA_INICIAL].valores);
(function loop(){
  requestAnimationFrame(loop);
  renderer.render(scene, camera);
})();
// Indicador removido somente depois de carregar as texturas.
</script>
</body>
</html>
"""


def main():
    if not os.path.isfile(VENDOR) or os.path.getsize(VENDOR) < 100000:
        print("ERRO: preview/vendor/three.min.js nao encontrado — a previa e "
              "autocontida e precisa dele (copie de outro preview do mod).")
        return 1
    with open(VENDOR, encoding="utf-8") as f:
        three = f.read()

    cores = paleta_de_cores()
    ordem = sorted(cores)
    onde = {bid: i for i, bid in enumerate(ordem)}
    vidro = {onde[bid]: 1 for bid in ordem if bid in VIDRO}
    gondola = onde["intoxicantes:prateleira_mercado"]
    lampada = onde["intoxicantes:lampada_led"]

    # rotulo humano de cada bloco (usado na ficha e na legenda)
    rotulos = {
        "intoxicantes:prateleira_mercado": "gondola (vende)",
        "intoxicantes:caixa_mercado": "caixa / registradora",
        "intoxicantes:porta_grade": "porta-grade",
        "intoxicantes:placa_esquinao": "letreiro da esquina",
        "intoxicantes:painel_led": "painel de LED",
        "intoxicantes:lampada_led": "lampada LED de teto",
        "intoxicantes:poste_luz": "poste de luz",
        "intoxicantes:hidrante": "hidrante",
        "intoxicantes:faixa_pedestre": "faixa de pedestre",
        "intoxicantes:asfalto": "asfalto",
        "minecraft:smooth_quartz": "piso do salao",
        "minecraft:barrel": "barril de estoque",
        "minecraft:light_blue_stained_glass": "vidro de fachada",
        "minecraft:white_concrete": "fachada",
        "minecraft:smooth_stone_slab": "balcao",
        "minecraft:black_stained_glass_pane": "vitrine",
        "minecraft:shroomlight": "luz de teto",
        "minecraft:oak_wall_sign": "quadro de precos",
        "minecraft:lantern": "lanterna do balcao",
    }
    rotulo_idx = {str(onde[bid]): rotulos.get(bid, bid) for bid in ordem if bid in rotulos}

    variantes = {}
    for regiao in gerador.REGIOES:
        paleta = gerador.paleta_da_regiao(regiao)
        blocos = []
        for y, camada in enumerate(gerador.ROWS):
            for z, linha in enumerate(camada):
                for x, ch in enumerate(linha):
                    estado = gerador.estado_do_template(paleta, x, y, z)
                    bid = estado["id"]
                    if bid == "minecraft:air":
                        continue
                    blocos.append([x, y, z, onde[bid], geometria(estado, bid), ch,
                            GIROS.get((estado.get("properties") or {}).get("facing"), 0)])
        variantes[regiao] = blocos

    import gen_mobilia_mercado, gen_caixa_mercado, gen_prateleira
    from gen_lampada_led import data_url
    import preview_mobilia_mercado as estudio
    entradas = gen_mobilia_mercado.preview_entries() + gen_caixa_mercado.preview_entries()
    modelos = {onde['intoxicantes:' + p['id']]: p for p in entradas}
    modelos[onde['intoxicantes:caixa_mercado']]['metades'] = {
        'lower': gen_caixa_mercado.MODELO_BASE, 'upper': gen_caixa_mercado.MODELO_TOPO}
    modelos[gondola] = {'model': gen_prateleira.modelo(), 'textures': {
        'intoxicantes:block/' + nome: data_url(func()) for nome,func in (
            ('prateleira_mercado',gen_prateleira.tex_metal),
            ('prateleira_mercado_prateleira',gen_prateleira.tex_prateleira),
            ('prateleira_mercado_etiqueta',gen_prateleira.tex_etiqueta))}}
    dados = {
        'modelos': modelos,
        'corredores': [[13.5,z+.5,19,1] for z in (5,7,8,10)] +
                      [[x+.5,8,1,6] for x in (7,9,17,19)] +
                      [[x+.5,z+.5,1,1] for x in gerador.COLUNAS_ILHA
                       for z in gerador.FILEIRAS_ILHA],
        "ids": ordem,
        "cores": [cores[bid] for bid in ordem],
        "vidro": vidro,
        "gondola": gondola,
        "lampada": lampada,
        "gago": gerador.GAGO_POS,
        "variantes": variantes,
    }
    camadas = {"teto": "Teto", "fachada": "Fachada", "patio": "Patio",
               "gondolas": "gondolas", "mobilia": "mobilia"}
    tudo = {"teto": True, "fachada": True, "patio": True, "gondolas": True, "mobilia": True}
    salao = dict(tudo, teto=False, fachada=False, patio=False)
    vistas = [
        {"nome": "Lote inteiro", "camadas": tudo,
         "valores": {"alvo": [13.5, 1.5, 13], "dist": 46,
                     "phi": math.pi * .40, "theta": math.pi * .25}},
        {"nome": "Salao (sem teto)", "camadas": salao,
         "valores": {"alvo": [13.5, 1.6, 8], "dist": 30,
                     "phi": math.pi * .40, "theta": math.pi * .16}},
        {"nome": "Corredor central", "camadas": salao,
         "valores": {"alvo": [12.5, 1.5, 9.5], "dist": 14,
                     "phi": math.pi * .44, "theta": math.pi * .08}},
        {"nome": "Balcao + Gago", "camadas": salao,
         "valores": {"alvo": [10.5, 2, 12], "dist": 9,
                     "phi": math.pi * .40, "theta": math.pi * .75}},
        {"nome": "Ilhas de gondola", "camadas": salao,
         "valores": {"alvo": [13.5, 1.5, 8], "dist": 27,
                     "phi": math.pi * .12, "theta": math.pi}},
        {"nome": "Mobilia lateral", "camadas": salao,
         "valores": {"alvo": [7, 1.4, 9], "dist": 22,
                     "phi": math.pi * .38, "theta": math.pi * .26}},
    ]

    html = (HTML.replace("/*__THREE__*/", three)
            .replace("__DADOS__", json.dumps(dados, ensure_ascii=False))
            .replace("__CAMADAS__", json.dumps(camadas, ensure_ascii=False))
            .replace("__VISTAS__", json.dumps(vistas, ensure_ascii=False))
            .replace("__INICIAL__", "4")
            .replace('__MODEL_RENDERER__', estudio.HTML.split('/* ---------- modelo -> malha ---------- */',1)[1].split('function constroi(peca)',1)[0]))
    # os rotulos entram no JS como objeto literal (id numerico -> texto)
    mapa = json.dumps(rotulo_idx, ensure_ascii=False)
    html = html.replace('<script>\n"use strict";',
                        '<script>\n"use strict";\nconst DADO_ROTULO = ' + mapa + ';')

    os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
    with open(SAIDA, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Prévia 3D gerada: {os.path.relpath(SAIDA, RAIZ)} "
          f"({os.path.getsize(SAIDA) // 1024} KB) — "
          f"{len(ordem)} blocos distintos, 3 peles")
    return 0


if __name__ == "__main__":
    sys.exit(main())