#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SNC Adventures — Weapon Studio (gerador)
========================================

Gera `preview/estudio-armas.html`: estúdio 3D autocontido (offline) para
inspeção e validação dos modelos 3D das armas, na aba Preview do Freebuff.

Regras do projeto (AGENTS.md) que este gerador cumpre:
- Gerador é a FONTE PRINCIPAL: editar aqui, nunca no HTML gerado.
- Lê o MODELO REAL (`models/item/*.json`), a TEXTURA REAL (embutida em
  base64) e os TRANSFORMS reais de `display` — nada de cópia manual.
- Remapeamento de eixos DOCUMENTADO (regra de remapeamento de armas):

    Convenção do projeto: no JSON do MC, o comprimento da arma fica no eixo Y
    (cano para +Y) e a altura no eixo Z. A cena THREE usa:
        T(x, y, z)_json = (y, z, x)_cena
        comprimento (Y_json) -> X_cena | altura (Z_json) -> Y_cena | espessura (X_json) -> Z_cena
    É uma permutação CÍCLICA (determinante +1): sem espelhamento, sentidos de
    rotação preservados. O muzzle aponta para +X_cena.

    Transforms de `display`: no jogo, a rotação é R_mc = Rz(rz)·Ry(ry)·Rx(rx)
    (ângulos POSITIVOS do JSON, ordem mulPose do JOML) aplicada no ESPAÇO DO
    MODELO, onde o cano é +Y. No estúdio a geometria já está remapeada por T,
    então a mesma rotação é expressa no espaço da cena por CONJUGAÇÃO:
        R_cena = T · R_mc · T⁻¹
    e a translação é aplicada no referencial de vista (antes da rotação),
    como no vanilla: position = translation/16.

    O grupo `alinhamento` (rotação T⁻¹, ciclo inverso) converte o resultado
    para o referencial de vista do jogo: após o transform de primeira pessoa,
    o cano aponta para −Z (frente da tela) e o topo da arma para +Y — as
    vistas 1ª pessoa, 3ª pessoa e GUI usam câmeras "normais" olhando −Z.

- Vista em primeira pessoa usa o transform `firstperson_righthand` REAL.
- AUTO-DESCOBERTA: o gerador varre `models/item/*.json` e inclui sozinho
  todo modelo com `elements` (textura resolvida das faces). A tabela
  WEAPONS abaixo é só o EXTRA (rótulo, munição pareada, articulações).
- MUNIÇÕES: itens 2D (`MUNICOES`) são extrudados em voxel no estúdio,
  fiel ao `item/generated` do Minecraft (cada pixel → cuboide), com vista
  de Comparação de escala ao lado da arma pareada.
- SAÍDA OFFLINE: além de `preview/estudio-armas.html`, o estúdio é
  empacotado em `dist/estudio-armas-offline.html` (+ SHA256) para
  compartilhar fora do repositório.

Uso (a partir da raiz do projeto do mod):
    python tools/gen_estudio_armas.py

Saída: preview/estudio-armas.html (+ preview/vendor/three.min.js deve
existir; o gerador baixa sozinho se faltar).
"""

import base64
import datetime
import hashlib
import json
import os
import struct

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(RAIZ, "src", "main", "resources", "assets", "intoxicantes")
THREE_VENDOR = os.path.join(RAIZ, "preview", "vendor", "three.min.js")
SAIDA = os.path.join(RAIZ, "preview", "estudio-armas.html")
SAIDA_DIST = os.path.join(RAIZ, "dist", "estudio-armas-offline.html")

THREE_URL = "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.min.js"

# ---------------------------------------------------------------------------
# Configuração EXTRA por arma (a descoberta é automática — ver descobrir_armas)
# ---------------------------------------------------------------------------
# O gerador varre `models/item/*.json` e inclui sozinho todo modelo 3D com
# `elements` (textura resolvida das faces). Aqui vai só o que não se deduz
# dos arquivos:
#   municao          : id da munição pareada (vista de Comparação de escala)
#   comprimentoRealMm: comprimento real da arma (mm) — calibra a comparação
#   label            : nome bonito (padrão: id em título)
#   groups           : articulações (regra: grupos/pivôs/eixos reais do
#                      modelo, nunca animação fake)
#   match : prefixos de `name` dos elements que pertencem ao grupo
#   type  : 'rotate' (graus) ou 'slide' (unidades de modelo)
#   axis  : eixo no ESPAÇO DO JSON Minecraft
#   pivot : [x,y,z] explícito no espaço do JSON, ou:
#           'group_top_center'    -> (cx, maxY, cz) do bbox do grupo
#           'group_bottom_center' -> (cx, minY, cz) do bbox do grupo
#           'group_center_xz'     -> (cx, cy, cz) do bbox do grupo
WEAPONS = {
    "escopeta": {
        "label": "Escopeta 12",
        "municao": "cartucho",
        "comprimentoRealMm": 1050.0,
        "groups": [
            {
                "id": "pump",
                "label": "Pump (forend + action bars)",
                "match": ["forend_", "action_bar_"],
                "type": "slide",
                "axis": "y",
                "range": [-3.5, 3.5],
                "pivot": [8.0, 0.0, 8.0],
                "default": 0.0,
            },
            {
                "id": "trigger",
                "label": "Gatilho",
                "match": ["trigger"],
                "type": "rotate",
                "axis": "x",
                "range": [-14.0, 14.0],
                "pivot": "group_top_center",
                "default": 0.0,
            },
        ],
    },
    "revolver": {
        "label": "Revólver .38",
        "municao": "cartucho_38",
        "comprimentoRealMm": 240.0,
        "groups": [
            {
                "id": "tambor",
                "label": "Tambor (cilindro + câmaras)",
                "match": ["tambor", "camara", "flute"],
                "type": "rotate",
                "axis": "y",
                "range": [0.0, 360.0],
                "pivot": "group_center_xz",
                "default": 0.0,
            },
            {
                "id": "martelo",
                "label": "Martelo",
                "match": ["martelo"],
                "type": "rotate",
                "axis": "x",
                "range": [-32.0, 0.0],
                "pivot": "group_bottom_center",
                "default": 0.0,
            },
            {
                "id": "gatilho",
                "label": "Gatilho",
                "match": ["gatilho"],
                "type": "rotate",
                "axis": "x",
                "range": [-12.0, 12.0],
                "pivot": "group_top_center",
                "default": 0.0,
            },
        ],
    },
}

# Munições exibidas no estúdio: o item 2D do jogo (sprite `item/generated`)
# extrudado em voxel, com calibre real para a vista de Comparação.
MUNICOES = {
    "cartucho": {
        "label": "Cartucho Calibre 12",
        "sprite": "textures/item/cartucho.png",
        "comprimentoRealMm": 70.0,  # casco 2¾" ≈ 70 mm total
    },
    "cartucho_38": {
        "label": "Cartucho .38",
        "sprite": "textures/item/cartucho_38.png",
        "comprimentoRealMm": 39.0,  # .38 Special ≈ 39 mm total
    },
}


# ---------------------------------------------------------------------------
# Leitura dos dados reais
# ---------------------------------------------------------------------------

def ler_modelo(caminho_relativo):
    with open(os.path.join(ASSETS, caminho_relativo), "r", encoding="utf-8") as f:
        return json.load(f)


def ler_textura_base64(caminho_relativo):
    """Lê o PNG e devolve (data_uri, largura, altura)."""
    caminho = os.path.join(ASSETS, caminho_relativo)
    with open(caminho, "rb") as f:
        dados = f.read()
    w, h = struct.unpack(">II", dados[16:24])
    return "data:image/png;base64," + base64.b64encode(dados).decode("ascii"), w, h


def caminho_textura(ref):
    """'intoxicantes:item/escopeta' -> 'textures/item/escopeta.png' (se existir)."""
    if not isinstance(ref, str) or not ref:
        return None
    nome = ref.split(":")[-1]
    relativo = "textures/%s.png" % nome
    return relativo if os.path.isfile(os.path.join(ASSETS, relativo)) else None


def resolver_textura(modelo):
    """Resolve o PNG da textura REAL a partir das faces dos elements."""
    texturas = modelo.get("textures", {}) or {}
    for e in modelo.get("elements", []):
        for face in (e.get("faces") or {}).values():
            ref = face.get("texture")
            if isinstance(ref, str) and ref.startswith("#"):
                encontrado = caminho_textura(texturas.get(ref[1:]))
                if encontrado:
                    return encontrado
    for chave in ("particle", "texture", "0", "layer0"):
        encontrado = caminho_textura(texturas.get(chave))
        if encontrado:
            return encontrado
    return None


def classificar(id_item, modelo):
    """'arma' (portátil: tem transform de primeira/terceira pessoa) ou 'outro'."""
    display = modelo.get("display", {}) or {}
    if "firstperson_righthand" in display or "thirdperson_righthand" in display:
        return "arma"
    return "outro"


def descobrir_armas():
    """Varre `models/item/*.json` e devolve {id: config} de todo modelo 3D
    (com `elements`) cuja textura seja resolvível. Armas novas do projeto
    entram no estúdio SEM editar este gerador (só articulações, se quiser)."""
    pasta = os.path.join(ASSETS, "models", "item")
    achados = {}
    for nome in sorted(os.listdir(pasta)):
        if not nome.endswith(".json"):
            continue
        id_item = nome[:-5]
        try:
            modelo = ler_modelo("models/item/" + nome)
        except Exception:  # noqa: BLE001 — JSON inválido não derruba o estúdio
            continue
        if not (modelo.get("elements") or []):
            continue
        textura = resolver_textura(modelo)
        if not textura:
            print("AVISO: %s tem elements, mas nenhuma textura resolvível — ignorado" % id_item)
            continue
        cfg = {
            "model": "models/item/" + nome,
            "texture": textura,
            "label": WEAPONS.get(id_item, {}).get("label", id_item.replace("_", " ").title()),
            "groups": [],
        }
        cfg.update({k: v for k, v in WEAPONS.get(id_item, {}).items() if k != "label"})
        achados[id_item] = cfg
    return achados


def montar_item_2d(id_item):
    """Extruda o sprite de uma munição em voxel, fiel ao `item/generated` do
    Minecraft: o sprite ocupa o espaço 0..16 do modelo (cada pixel vira um
    cuboide de lado s = 16/largura, plano centralizado em z = 8), com UVs
    reais por pixel. Linhas contíguas de pixels viram UM element."""
    try:
        from PIL import Image
    except ImportError:
        print("AVISO: PIL indisponível — munição %s ignorada" % id_item)
        return None
    cfg = MUNICOES[id_item]
    uri, tw, th = ler_textura_base64(cfg["sprite"])
    img = Image.open(os.path.join(ASSETS, cfg["sprite"])).convert("RGBA")
    px_img = img.load()
    w, h = img.size
    s = 16.0 / w
    elementos = []
    for py in range(h):
        y0 = (h - 1 - py) * s  # linha 0 do PNG (topo) fica no alto do modelo
        x = 0
        while x < w:
            if px_img[x, py][3] > 0:
                x0 = x
                while x < w and px_img[x, py][3] > 0:
                    x += 1
                uv = [round(x0 * s, 3), round(py * s, 3), round(x * s, 3), round((py + 1) * s, 3)]
                elementos.append({
                    "name": "px_%d_%d" % (py, x0),
                    "from": [round(x0 * s, 3), round(y0, 3), round(8 - s / 2, 3)],
                    "to": [round(x * s, 3), round(y0 + s, 3), round(8 + s / 2, 3)],
                    "rotation": None,
                    "faces": {k: {"uv": uv} for k in ("up", "down", "north", "south", "east", "west")},
                })
            else:
                x += 1
    if not elementos:
        print("AVISO: sprite de %s é todo transparente — munição ignorada" % id_item)
        return None
    lo, hi = bbox_dos_elementos(elementos)
    return {
        "label": cfg["label"],
        "kind": "municao",
        "tex": uri,
        "texW": tw,
        "texH": th,
        "elements": elementos,
        "display": {},
        "groups": [],
        "stats": {
            "elements": len(elementos),
            "comprimento": round(hi[1] - lo[1], 1),
            "altura": round(hi[2] - lo[2], 1),
            "largura": round(hi[0] - lo[0], 1),
            "tex": "%dx%d" % (tw, th),
            "bbox_min": [round(v, 2) for v in lo],
            "bbox_max": [round(v, 2) for v in hi],
        },
    }


def bbox_dos_elementos(elementos):
    lo = [min(e["from"][i] for e in elementos) for i in range(3)]
    hi = [max(e["to"][i] for e in elementos) for i in range(3)]
    return lo, hi


def stats_da_arma(modelo, largura_tex, altura_tex):
    elementos = modelo["elements"]
    lo, hi = bbox_dos_elementos(elementos)
    # Convenção do projeto: comprimento no eixo Y do JSON (cano para +Y).
    return {
        "elements": len(elementos),
        "comprimento": round(hi[1] - lo[1], 1),
        "altura": round(hi[2] - lo[2], 1),
        "largura": round(hi[0] - lo[0], 1),
        "tex": "%dx%d" % (largura_tex, altura_tex),
        "bbox_min": [round(v, 2) for v in lo],
        "bbox_max": [round(v, 2) for v in hi],
    }


def resolver_pivo(grupo, elementos_do_grupo):
    tipo = grupo["pivot"]
    if isinstance(tipo, (list, tuple)):
        return [float(v) for v in tipo]
    lo, hi = bbox_dos_elementos(elementos_do_grupo)
    cx = (lo[0] + hi[0]) / 2.0
    cz = (lo[2] + hi[2]) / 2.0
    if tipo == "group_top_center":
        return [cx, hi[1], cz]
    if tipo == "group_bottom_center":
        return [cx, lo[1], cz]
    if tipo == "group_center_xz":
        return [cx, (lo[1] + hi[1]) / 2.0, cz]
    raise ValueError("pivot desconhecido: %r" % (tipo,))


def montar_dados():
    armas = {}
    for chave, cfg in descobrir_armas().items():
        modelo = ler_modelo(cfg["model"])
        tex_uri, tw, th = ler_textura_base64(cfg["texture"])
        elementos = []
        for e in modelo["elements"]:
            elementos.append(
                {
                    "name": e.get("name", "sem_nome"),
                    "from": [float(v) for v in e["from"]],
                    "to": [float(v) for v in e["to"]],
                    "rotation": e.get("rotation"),
                    # UVs reais por face (usadas pelo renderer do estúdio)
                    "faces": {
                        k: {"uv": [float(u) for u in f["uv"]]}
                        for k, f in e.get("faces", {}).items() if "uv" in f
                    },
                }
            )
        nomes = {e["name"]: e for e in elementos}
        grupos = []
        for g in cfg["groups"]:
            membros = [n for n in nomes if any(n.startswith(p) for p in g["match"])]
            if not membros:
                print("AVISO: grupo %r sem elementos em %s" % (g["id"], chave))
                continue
            grupo_els = [nomes[n] for n in membros]
            pivo = resolver_pivo(g, grupo_els)
            grupos.append(
                {
                    "id": g["id"],
                    "label": g["label"],
                    "type": g["type"],
                    "axis": g["axis"],
                    "range": [float(v) for v in g["range"]],
                    "pivot": pivo,
                    "members": membros,
                    "default": float(g.get("default", 0.0)),
                }
            )
        armas[chave] = {
            "label": cfg["label"],
            "kind": "arma" if classificar(chave, modelo) == "arma" else "outro",
            "tex": tex_uri,
            "texW": tw,
            "texH": th,
            "elements": elementos,
            "display": modelo.get("display", {}),
            "groups": grupos,
            "stats": stats_da_arma(modelo, tw, th),
            "municao": cfg.get("municao") if cfg.get("municao") in MUNICOES else None,
            "comprimentoRealMm": float(cfg.get("comprimentoRealMm", 0.0)) or None,
        }
    itens2d = {}
    for chave in MUNICOES:
        item = montar_item_2d(chave)
        if item:
            item["comprimentoRealMm"] = float(MUNICOES[chave]["comprimentoRealMm"])
            itens2d[chave] = item
    return {"weapons": armas, "itens2d": itens2d, "geradoEm": datetime.date.today().isoformat()}


# ---------------------------------------------------------------------------
# three.min.js (vendor offline)
# ---------------------------------------------------------------------------

def garantir_three():
    if os.path.isfile(THREE_VENDOR) and os.path.getsize(THREE_VENDOR) > 100000:
        with open(THREE_VENDOR, "r", encoding="utf-8") as f:
            return f.read()
    print("three.min.js nao encontrado em preview/vendor — baixando de %s" % THREE_URL)
    try:
        from urllib.request import urlopen

        dados = urlopen(THREE_URL, timeout=60).read()
    except Exception as exc:  # noqa: BLE001
        raise SystemExit(
            "Nao consegui obter three.min.js (%s). Baixe manualmente de\n"
            "  %s\n e salve em preview/vendor/three.min.js" % (exc, THREE_URL)
        )
    os.makedirs(os.path.dirname(THREE_VENDOR), exist_ok=True)
    with open(THREE_VENDOR, "wb") as f:
        f.write(dados)
    return dados.decode("utf-8")


# ---------------------------------------------------------------------------
# Aplicação do estúdio (JS embutido no HTML gerado)
# ---------------------------------------------------------------------------

APP_JS = r"""
'use strict';
/* SNC Adventures — Weapon Studio
   Gerado por tools/gen_estudio_armas.py — NAO editar o HTML gerado.
   Remapeamento e matemática de transforms: documentado no gerador. */

const DATA = window.STUDIO_DATA;
const ARMA_INICIAL = Object.keys(DATA.weapons)[0];
const DEG = Math.PI / 180;

/* Remapeamento T: (x,y,z)_json -> (y,z,x)_cena. Permutação cíclica, det +1. */
function T(p) { return new THREE.Vector3(p[1], p[2], p[0]); }
const EIXO_VEC = { x: [1,0,0], y: [0,1,0], z: [0,0,1] }; // no espaço do JSON
/* Eixo JSON -> eixo THREE (para rotation de grupos: rotação sobre T(eixo)). */
const EIXO_THREE = { x: 'z', y: 'x', z: 'y' };

/* Matrizes coluna-major (compatíveis com THREE.Matrix4.fromArray). */
function mat4Mul(a, b) {
  const o = new Array(16);
  for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) {
    o[c*4+r] = a[r]*b[c*4] + a[4+r]*b[c*4+1] + a[8+r]*b[c*4+2] + a[12+r]*b[c*4+3];
  }
  return o;
}
function mat4RotX(d) { const c=Math.cos(d*DEG), s=Math.sin(d*DEG); return [1,0,0,0, 0,c,s,0, 0,-s,c,0, 0,0,0,1]; }
function mat4RotY(d) { const c=Math.cos(d*DEG), s=Math.sin(d*DEG); return [c,0,-s,0, 0,1,0,0, s,0,c,0, 0,0,0,1]; }
function mat4RotZ(d) { const c=Math.cos(d*DEG), s=Math.sin(d*DEG); return [c,s,0,0, -s,c,0,0, 0,0,1,0, 0,0,0,1]; }
const MAT_T_INV = [0,1,0,0, 0,0,1,0, 1,0,0,0, 0,0,0,1];    // T⁻¹ (ciclo inverso)

/* R_mc = Rz·Ry·Rx com os ângulos POSITIVOS do display (ordem mulPose). */
function quatDisplay(rot) {
  const r = rot || [0, 0, 0];
  const m = mat4Mul(mat4RotZ(r[2]), mat4Mul(mat4RotY(r[1]), mat4RotX(r[0])));
  return new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().fromArray(m));
}
const QUAT_T_INV = new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().fromArray(MAT_T_INV));

/* ---------- Construção da malha de um element (JSON -> THREE) ------------- */
const DIRS = [
  { id: 'px', n: [ 1, 0, 0] }, { id: 'nx', n: [-1, 0, 0] },
  { id: 'py', n: [ 0, 1, 0] }, { id: 'ny', n: [ 0,-1, 0] },
  { id: 'pz', n: [ 0, 0, 1] }, { id: 'nz', n: [ 0, 0,-1] },
];
const FACE_KIND = { px: 'east', nx: 'west', py: 'up', ny: 'down', pz: 'south', nz: 'north' };
/* brilho por face (simula a luz direcional do MC): cima clara, baixo escura */
const FACE_TINT = { py: 1.14, ny: 0.78, px: 0.96, nx: 0.96, pz: 1.0, nz: 1.0 };

function constroiElemento(el, materiais, centro) {
  const posicoes = [];
  const uvs = [];
  const normais = [];
  const indices = [];
  const matGrupos = []; // [materialIndex, start, count]

  DIRS.forEach((d, idxDir) => {
    const info = el.faces[FACE_KIND[d.id]];
    if (!info) return;
    const eixo = { x: 0, y: 1, z: 2 }[d.id[1]];
    const pEixo = d.id[0] === 'p' ? el.to[eixo] : el.from[eixo];
    const outros = [0, 1, 2].filter(i => i !== eixo);
    const p = (u, v) => {
      const w = [0, 0, 0];
      w[eixo] = pEixo; w[outros[0]] = u; w[outros[1]] = v;
      return w;
    };
    const quad = [p(el.from[outros[0]], el.from[outros[1]]), p(el.to[outros[0]], el.from[outros[1]]),
                  p(el.to[outros[0]], el.to[outros[1]]), p(el.from[outros[0]], el.to[outros[1]])];

    // UV em unidades MC (0..16 sobre a textura inteira), do JSON
    const uv = info.uv || [0, 0, 16, 16];
    const uu = [uv[0], uv[2], uv[2], uv[0]];
    const vv = [uv[1], uv[1], uv[3], uv[3]];

    const base = posicoes.length / 3;
    quad.forEach((v, i) => {
      const t = T(v);
      posicoes.push(t.x - centro.x, t.y - centro.y, t.z - centro.z);
      uvs.push(uu[i] / 16, 1 - vv[i] / 16);
      const n = T(d.n);
      normais.push(n.x, n.y, n.z);
    });
    indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
    matGrupos.push([idxDir, base, 6]);
  });

  if (!posicoes.length) return null;
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(posicoes, 3));
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
  geo.setAttribute('normal', new THREE.Float32BufferAttribute(normais, 3));
  geo.setIndex(indices);
  matGrupos.forEach(g => geo.addGroup(g[1], g[2], g[0]));
  const mesh = new THREE.Mesh(geo, materiais);
  mesh.castShadow = true; mesh.receiveShadow = true;
  return mesh;
}

/* ---------- Cena e hierarquia ----------------------------------------------
   Ordem das rotações reproduz o jogo: no vetor do modelo, primeiro T⁻¹
   (volta ao espaço JSON onde R_mc age) e depois R_mc — então, do mais
   profundo pro mais externo: modelo → alinhamento(T⁻¹) → rotacionado(R_mc)
   → posicionado(translation/scale do display, em espaço de vista).
   Em fórmulas: world = t + s·(R_mc·(T⁻¹·local))  [unidades de modelo].       */
let renderer, scene, camera;
let raizArma, modelo, alinhamento, posicionado, rotacionado;
let boneco, maoObj, terreno, grade;
let orbit; let estadoVista = 'perspectiva';
let armaAtual = null; let gruposAnim = [];
let estadoAba = 'armas'; let armaAnterior = null;
let cenaComparacao = null; let refFrame = null; let refChao = null;

function criaMateriais(textura) {
  const tex = new THREE.TextureLoader().load(textura);
  tex.magFilter = THREE.NearestFilter; tex.minFilter = THREE.NearestFilter;
  tex.colorSpace = THREE.SRGBColorSpace;
  return DIRS.map(d => new THREE.MeshStandardMaterial({
    map: tex, metalness: 0.55, roughness: 0.5, side: THREE.DoubleSide,
    color: new THREE.Color(FACE_TINT[d.id], FACE_TINT[d.id], FACE_TINT[d.id]),
  }));
}

function constroiArma(chave) {
  const arma = DATA.weapons[chave];
  if (raizArma) {
    scene.remove(raizArma);
    raizArma.traverse(o => { if (o.geometry) o.geometry.dispose(); });
  }
  gruposAnim = [];
  raizArma = new THREE.Group();
  modelo = new THREE.Group();
  alinhamento = new THREE.Group(); alinhamento.quaternion.copy(QUAT_T_INV);
  posicionado = new THREE.Group();
  rotacionado = new THREE.Group();

  const materiais = criaMateriais(arma.tex);
  const ancora = T([8, 8, 8]);
  /* Os elements são posicionados relativos a `ancora` = centro do cubo 16³:
     equivale à centralização (v/16 − 0.5) do ItemRenderer — a rotação de
     display gira em torno do centro do modelo, como no jogo. NÃO somar
     offset extra aqui. */

  const pertence = {};
  arma.groups.forEach(g => g.members.forEach(m => { pertence[m] = g; }));

  const gruposObj = {};
  arma.groups.forEach(g => {
    const obj = new THREE.Group();
    obj.position.copy(T(g.pivot)).sub(ancora);
    gruposObj[g.id] = obj;
    rotacionado.add(obj);
    gruposAnim.push({ cfg: g, obj, valor: g.default || 0 });
  });

  arma.elements.forEach(el => {
    const g = pertence[el.name];
    const centroT = T([(el.from[0] + el.to[0]) / 2, (el.from[1] + el.to[1]) / 2, (el.from[2] + el.to[2]) / 2]);
    let mesh = null;
    try { mesh = constroiElemento(el, materiais, centroT); } catch (e) { console.warn('elemento falhou', el.name, e); }
    if (!mesh) return;
    mesh.name = el.name;
    mesh.position.copy(centroT).sub(g ? T(g.pivot) : ancora);
    (g ? gruposObj[g.id] : modelo).add(mesh);
  });

  rotacionado.add(alinhamento);
  alinhamento.add(modelo);
  posicionado.add(rotacionado);
  raizArma.add(posicionado);

  // mão ilustrativa (1ª pessoa): vive dentro de `rotacionado`, no espaço do
  // receiver, e acompanha o transform real em todas as vistas com display.
  maoObj = new THREE.Group();
  const matMao = new THREE.MeshStandardMaterial({ color: 0xc08a6e, roughness: 0.85 });
  const palma = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.2, 0.16), matMao);
  palma.castShadow = true; maoObj.add(palma);
  maoObj.position.set(0.04, -0.01, -0.03);
  maoObj.rotation.set(0.5, 0, -0.3);
  maoObj.visible = false;
  posicionado.add(maoObj); // espaço de vista: acompanha o transform, sem girar com R_mc

  scene.add(raizArma);
  return arma;
}

/* ---------- Munições (voxel do sprite) e cenário de comparação ------------ */
function letraTex(caractere) {
  const c = document.createElement('canvas'); c.width = 128; c.height = 64;
  const g = c.getContext('2d');
  g.fillStyle = '#10161c'; g.fillRect(0, 0, 128, 64);
  g.strokeStyle = '#3a4a5a'; g.lineWidth = 5; g.strokeRect(5, 5, 118, 54);
  g.fillStyle = '#e8b64c'; g.font = 'bold 34px Segoe UI, sans-serif';
  g.textAlign = 'center'; g.textBaseline = 'middle';
  g.fillText(caractere, 64, 34);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

/* Constrói (uma vez) o voxel de cada munição + o referencial de escala:
   bloco de 1 m com a letra no topo e piso circular da comparação. */
function constroiItens2d() {
  const ids = Object.keys(DATA.itens2d || {});
  if (!ids.length) return;
  const grupo = new THREE.Group();
  grupo.name = 'cenaComparacao';
  const ancora = T([8, 8, 8]);
  ids.forEach(id => {
    const item = DATA.itens2d[id];
    const gItem = new THREE.Group();
    gItem.name = 'item2d:' + id;
    const pertence = {};
    (item.groups || []).forEach(g => g.members.forEach(m => { pertence[m] = g; }));
    const gruposObj = {};
    (item.groups || []).forEach(g => {
      const obj = new THREE.Group();
      obj.position.copy(T(g.pivot)).sub(ancora);
      gruposObj[g.id] = obj; gItem.add(obj);
    });
    const materiais = criaMateriais(item.tex);
    item.elements.forEach(el => {
      const g = pertence[el.name];
      const centroT = T([(el.from[0] + el.to[0]) / 2, (el.from[1] + el.to[1]) / 2, (el.from[2] + el.to[2]) / 2]);
      let mesh = null;
      try { mesh = constroiElemento(el, materiais, centroT); } catch (e) { console.warn('elemento falhou', el.name, e); }
      if (!mesh) return;
      mesh.name = el.name;
      mesh.position.copy(centroT).sub(g ? T(g.pivot) : ancora);
      (g ? gruposObj[g.id] : gItem).add(mesh);
    });
    grupo.add(gItem);
  });
  grupo.visible = false;
  cenaComparacao = grupo;
  scene.add(grupo);

  refFrame = new THREE.Group();
  const matRef = new THREE.MeshStandardMaterial({ color: 0x39434e, roughness: 0.92 });
  const bloco = new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1), matRef);
  bloco.position.y = 0.5; bloco.castShadow = true; bloco.receiveShadow = true;
  refFrame.add(bloco);
  const bordas = new THREE.LineSegments(
    new THREE.EdgesGeometry(new THREE.BoxGeometry(1, 1, 1)),
    new THREE.LineBasicMaterial({ color: 0x64788c })
  );
  bordas.position.y = 0.5; refFrame.add(bordas);
  const plano = new THREE.Mesh(new THREE.PlaneGeometry(0.68, 0.68), new THREE.MeshBasicMaterial({ map: letraTex('1 m') }));
  plano.rotation.x = -Math.PI / 2; plano.position.y = 1.003;
  refFrame.add(plano);
  refFrame.position.set(-0.72, 0, 0.55);
  refFrame.visible = false;
  scene.add(refFrame);

  refChao = new THREE.Mesh(
    new THREE.CircleGeometry(2.3, 48),
    new THREE.MeshStandardMaterial({ color: 0x141b23, roughness: 1 })
  );
  refChao.rotation.x = -Math.PI / 2; refChao.position.y = 0.001; refChao.receiveShadow = true;
  refChao.visible = false;
  scene.add(refChao);
}

/* Sai da vista Comparação: esconde HUD/referencial e devolve a raiz da arma
   à cena com transform neutro (as vistas de estudo assumem isso). */
function ocultaComparacao() {
  if (cenaComparacao) cenaComparacao.visible = false;
  if (refFrame) refFrame.visible = false;
  if (refChao) refChao.visible = false;
  const hud = document.getElementById('comparacao-hud');
  if (hud) hud.style.display = 'none';
  if (raizArma && raizArma.parent !== scene) {
    scene.add(raizArma);
    raizArma.position.set(0, 0, 0); raizArma.scale.setScalar(1);
    rotacionado.position.set(0, 0, 0);
    posicionado.position.set(0, 0, 0); posicionado.scale.setScalar(1);
  }
}

function aplicaDisplayTransform(arma, disp, extra) {
  const r = disp && disp.rotation ? disp.rotation : [0, 0, 0];
  const t = disp && disp.translation ? disp.translation : [0, 0, 0];
  const s = disp && disp.scale ? disp.scale[0] : 1;
  /* Vistas com display: alinhamento = T⁻¹ (constante), rotacionado = R_mc,
     posicionado = translação/escala do display (espaço de vista). */
  alinhamento.quaternion.copy(QUAT_T_INV);
  rotacionado.quaternion.copy(quatDisplay(r));
  posicionado.position.set(t[0]/16, t[1]/16, t[2]/16);
  if (extra) posicionado.position.add(extra);
  posicionado.scale.setScalar(s || 1);
}

function aplicaGrupo(cfg, obj, valor) {
  if (cfg.type === 'rotate') {
    obj.rotation.set(0, 0, 0);
    obj.rotation[EIXO_THREE[cfg.axis]] = valor * DEG; // rotação sobre T(eixo), sentido preservado
  } else {
    const v = T(EIXO_VEC[cfg.axis]).multiplyScalar(valor);
    obj.position.copy(v);
  }
}

/* ---------- Órbita com damping (rotação livre, zoom, pan) ------------------ */
function criaOrbit() {
  const alvo = new THREE.Vector3(0.5, 0.6, 0);
  const esf = { r: 2.6, th: 0.55, ph: 1.25 };
  const dest = { r: esf.r, th: esf.th, ph: esf.ph };
  const alvoDest = alvo.clone();
  const dom = renderer.domElement;
  let arrastando = 0, px = 0, py = 0;

  dom.addEventListener('contextmenu', e => e.preventDefault());
  dom.addEventListener('pointerdown', e => { arrastando = e.button === 2 ? 2 : 1; px = e.clientX; py = e.clientY; dom.setPointerCapture(e.pointerId); });
  dom.addEventListener('pointerup', e => { arrastando = 0; dom.releasePointerCapture(e.pointerId); });
  dom.addEventListener('pointermove', e => {
    if (!arrastando) return;
    const dx = e.clientX - px, dy = e.clientY - py; px = e.clientX; py = e.clientY;
    if (arrastando === 1) { dest.th -= dx * 0.008; dest.ph = Math.min(2.9, Math.max(0.15, dest.ph - dy * 0.008)); }
    else {
      const fator = dest.r * 0.0016;
      const dir = new THREE.Vector3().subVectors(camera.position, alvo);
      const direita = new THREE.Vector3().crossVectors(dir, camera.up).normalize();
      const cima = new THREE.Vector3().crossVectors(direita, dir).normalize();
      alvoDest.addScaledVector(direita, -dx * fator).addScaledVector(cima, dy * fator);
    }
  });
  dom.addEventListener('wheel', e => {
    e.preventDefault();
    dest.r = Math.min(30, Math.max(0.3, dest.r * Math.pow(1.0015, e.deltaY)));
  }, { passive: false });

  return {
    alvo, esf, dest, alvoDest,
    atualiza() {
      esf.th += (dest.th - esf.th) * 0.14;
      esf.ph += (dest.ph - esf.ph) * 0.14;
      esf.r += (dest.r - esf.r) * 0.14;
      alvo.lerp(alvoDest, 0.14);
      camera.position.set(
        alvo.x + esf.r * Math.sin(esf.ph) * Math.sin(esf.th),
        alvo.y + esf.r * Math.cos(esf.ph),
        alvo.z + esf.r * Math.sin(esf.ph) * Math.cos(esf.th)
      );
      camera.up.set(0, 1, 0);
      camera.lookAt(alvo);
    },
    /* enquadra: câmera na DIREÇÃO dada, distância pela esfera envolvente do
       bbox real e pelo FOV efetivo (garante que nada corta em qualquer
       proporção de janela). direções em coordenadas de cena:
       cano = +X, cima = +Y, espessura = Z. */
    enquadra(obj, direcao, margem) {
      const box = new THREE.Box3().setFromObject(obj);
      const esfera = box.getBoundingSphere(new THREE.Sphere());
      const fovV = camera.fov * DEG;
      const fovH = 2 * Math.atan(Math.tan(fovV / 2) * camera.aspect);
      const d = (esfera.radius / Math.sin(Math.min(fovV, fovH) / 2)) * (margem || 1.12);
      alvoDest.copy(esfera.center);
      const mapa = {
        perspectiva: null,
        lateral_dir: [0, 0.06, 1],      // vê o lado direito (espessura) — perfil do comprimento
        lateral_esq: [0, 0.06, -1],
        topo: [0, 1, 0.0001],
        frente: [1, 0.06, 0],           // muzzle está em +X_cena
        traseira: [-1, 0.06, 0],
      };
      if (direcao && mapa[direcao]) {
        const v = new THREE.Vector3(...mapa[direcao]).normalize();
        dest.th = Math.atan2(v.x, v.z);
        dest.ph = Math.acos(Math.min(1, Math.max(-1, v.y)));
        dest.r = d;
      } else {
        dest.r = d;
        dest.th = 0.55; dest.ph = 1.25;
      }
    },
  };
}

/* ---------- Vistas -------------------------------------------------------
   Estudo (perspectiva/lateral/topo/frente/traseira): alinhamento identidade →
   cano para +X_cena, proporções naturais de leitura.
   Display (primeira/terceira/gui): alinhamento = T⁻¹ → referencial de vista
   do jogo (após o transform, cano para −Z da tela). */
const VISTAS = ['perspectiva', 'lateral_dir', 'lateral_esq', 'topo', 'frente', 'traseira', 'primeira', 'terceira', 'gui', 'comparacao'];
function setVista(nome) {
  estadoVista = nome;
  document.querySelectorAll('[data-vista]').forEach(b => b.classList.toggle('ativo', b.dataset.vista === nome));
  maoObj.visible = false; boneco.visible = false;
  terreno.visible = true; grade.visible = true;
  const eItem = !!DATA.itens2d[armaAtual];
  if (eItem && (nome === 'primeira' || nome === 'terceira')) nome = 'comparacao'; // munição não tem display próprio
  ocultaComparacao();
  raizArma.visible = true;

  if (nome === 'perspectiva' || nome === 'lateral_dir' || nome === 'lateral_esq' ||
      nome === 'topo' || nome === 'frente' || nome === 'traseira') {
    alinhamento.quaternion.identity(); rotacionado.quaternion.identity();
    posicionado.position.set(0, 0, 0); posicionado.scale.setScalar(1);
    if (eItem) {
      // estudo da munição: miniatura voxel do sprite no lugar da arma
      raizArma.visible = false;
      const gItem = cenaComparacao && cenaComparacao.getObjectByName('item2d:' + armaAtual);
      if (gItem) {
        cenaComparacao.visible = true;
        const sm = DATA.itens2d[armaAtual].stats;
        const cyM = (sm.bbox_min[2] + sm.bbox_max[2]) / 2 - 8;
        gItem.scale.setScalar(0.06);
        gItem.position.set(0, (sm.altura / 2 - cyM) * 0.06, 0);
        orbit.enquadra(gItem, nome);
      } else orbit.enquadra(modelo, nome);
    } else orbit.enquadra(modelo, nome);
  } else if (nome === 'primeira') {
    terreno.visible = false; grade.visible = false; // void: sem referencial estranho
    const arma = DATA.weapons[armaAtual];
    aplicaDisplayTransform(arma, arma.display.firstperson_righthand, null);
    ligaAcessorios();
    /* O stock real ficaria ATRÁS do olho (comportamento vanilla para itens
       longos). A câmera recua além do ponto de cruzamento para que nenhuma
       parte atravesse o near-plane: enquadra como FPS — receiver à direita,
       cano apontando adiante. */
    camera.position.set(-0.9, 0.45, 3.4);
    camera.up.set(0, 1, 0);
    camera.lookAt(0.0, 0.15, -2);
  } else if (nome === 'terceira') {
    const arma = DATA.weapons[armaAtual];
    aplicaDisplayTransform(arma, arma.display.thirdperson_righthand, new THREE.Vector3(0.42, 1.08, 0.1));
    boneco.visible = true;
    camera.position.set(1.9, 1.75, 4.8);
    camera.up.set(0, 1, 0);
    camera.lookAt(0.25, 1.05, 0);
  } else if (nome === 'gui') {
    terreno.visible = false; grade.visible = false;
    const arma = DATA.weapons[armaAtual];
    aplicaDisplayTransform(arma, arma.display.gui, null);
    orbit.enquadra(rotacionado, null);
    orbit.dest.th = 0; orbit.dest.ph = Math.PI / 2; // câmera de GUI: +Z olhando −Z
  } else if (nome === 'comparacao') {
    terreno.visible = false; grade.visible = false;
    const chaveArma = DATA.weapons[armaAtual] ? armaAtual
      : (armaAnterior || Object.keys(DATA.weapons).find(k => DATA.weapons[k].municao) || Object.keys(DATA.weapons)[0]);
    const arma = DATA.weapons[chaveArma] || {};
    const munId = (arma.municao && DATA.itens2d[arma.municao]) ? arma.municao : Object.keys(DATA.itens2d || {})[0];
    if (!munId) { orbit.enquadra(modelo, null); return; }
    const real = !document.getElementById('chk-real') || document.getElementById('chk-real').checked;
    const mun = DATA.itens2d[munId];
    /* Proporção REAL: cada objeto na própria escala (1 unit de modelo =
       comprimentoRealMm/16 mm; 1 unit de cena = 1 m = 1 bloco). */
    const escArma = arma.comprimentoRealMm ? (arma.comprimentoRealMm / 16) / 1000 : 0.065;
    const escMun = mun.comprimentoRealMm ? (mun.comprimentoRealMm / 16) / 1000 : 0.065;
    const kx = real ? escMun : 0.07;

    // arma no chão (cano para +X), na escala escolhida
    scene.remove(raizArma);
    alinhamento.quaternion.identity(); rotacionado.quaternion.identity();
    posicionado.position.set(0, 0, 0); posicionado.scale.setScalar(1);
    const s = arma.stats;
    const yCentro = s ? (s.bbox_min[2] + s.bbox_max[2]) / 2 - 8 : 0;
    rotacionado.position.set(0, s ? yCentro + s.altura / 2 : 0, 0);
    raizArma.position.set(0.38, 0, -0.4);
    raizArma.scale.setScalar(real ? escArma : 0.07);

    // munição voxel ao lado, na MESMA convenção de escala
    cenaComparacao.visible = true;
    const gItem = cenaComparacao.getObjectByName('item2d:' + munId);
    if (gItem) {
      const sm = mun.stats;
      const cyM = (sm.bbox_min[2] + sm.bbox_max[2]) / 2 - 8;
      gItem.scale.setScalar(kx);
      gItem.position.set(-0.5, (sm.altura / 2 - cyM) * kx, 0.5);
    }
    if (refFrame) refFrame.visible = true;
    if (refChao) refChao.visible = true;
    const hud = document.getElementById('comparacao-hud');
    if (hud) {
      hud.style.display = 'flex';
      const realLbl = document.getElementById('escala-lbl');
      if (realLbl) realLbl.textContent = real
        ? '1 bloco = 1 m · escala da proporção real'
        : 'escala de exibição (proporção real desligada)';
      const tamLbl = document.getElementById('tam-lbl');
      if (tamLbl) tamLbl.textContent =
        'Arma: ' + Math.round(arma.comprimentoRealMm || 0) + ' mm · ' + mun.label + ': ' + Math.round(mun.comprimentoRealMm || 0) + ' mm' +
        (arma.comprimentoRealMm && mun.comprimentoRealMm ? ' · razão ' + (arma.comprimentoRealMm / mun.comprimentoRealMm).toFixed(1) + '×' : '');
    }
    orbit.alvoDest.set(-0.1, 0.55, 0);
    orbit.dest.r = 4.2; orbit.dest.th = 0.72; orbit.dest.ph = 1.18;
  }
}
function ligaAcessorios() { maoObj.visible = !!document.getElementById('chk-mao').checked; }

/* ---------- UI ----------------------------------------------------------- */
function montaUI() {
  const sel = document.getElementById('armas');
  const abreAba = (k, kind) => {
    sel.querySelectorAll('.aba').forEach(x => x.classList.remove('ativo'));
    const b = sel.querySelector('[data-aba="' + k + '"]');
    if (b) b.classList.add('ativo');
    estadoAba = kind;
    carregaArma(k);
  };
  Object.keys(DATA.weapons).forEach(k => {
    const b = document.createElement('button');
    b.textContent = DATA.weapons[k].label; b.className = 'aba'; b.dataset.aba = k;
    b.onclick = () => abreAba(k, 'armas');
    sel.appendChild(b);
  });
  Object.keys(DATA.itens2d || {}).forEach(k => {
    const b = document.createElement('button');
    b.textContent = DATA.itens2d[k].label; b.className = 'aba'; b.dataset.aba = k;
    b.onclick = () => {
      if (DATA.weapons[armaAtual]) armaAnterior = armaAtual; // lembra a arma para a Comparação
      abreAba(k, 'itens2d');
    };
    sel.appendChild(b);
  });
  const primeiraAba = sel.querySelector('[data-aba="' + ARMA_INICIAL + '"]');
  if (primeiraAba) primeiraAba.classList.add('ativo');
  const nomes = {
    perspectiva: 'Perspectiva', lateral_dir: 'Lateral Dir.', lateral_esq: 'Lateral Esq.',
    topo: 'Topo', frente: 'Frente', traseira: 'Traseira', primeira: 'Primeira Pessoa',
    terceira: 'Terceira Pessoa', gui: 'GUI', comparacao: 'Comparação',
  };
  VISTAS.forEach(v => {
    const b = document.createElement('button');
    b.textContent = nomes[v] || v; b.dataset.vista = v; b.className = 'vista';
    if (v === 'primeira' || v === 'terceira') b.classList.add('so-armas'); // munição não tem transform próprio
    b.onclick = () => setVista(v);
    document.getElementById('vistas').appendChild(b);
  });
  document.getElementById('btn-reset').onclick = () => setVista(estadoVista);
  document.getElementById('btn-reset2').onclick = () => {
    if (estadoVista === 'primeira' || estadoVista === 'terceira' || estadoVista === 'gui' || estadoVista === 'comparacao') setVista(estadoVista);
    else {
      const gItem = DATA.itens2d[armaAtual] && cenaComparacao && cenaComparacao.getObjectByName('item2d:' + armaAtual);
      orbit.enquadra(gItem || modelo, estadoVista === 'perspectiva' ? null : estadoVista);
    }
  };
  document.getElementById('btn-full').onclick = () => {
    if (document.fullscreenElement) document.exitFullscreen();
    else document.documentElement.requestFullscreen();
  };
  document.getElementById('chk-mao').onchange = () => { if (estadoVista === 'primeira') ligaAcessorios(); };
  const chkHud = document.getElementById('chk-real');
  const chkPainel = document.getElementById('chk-real-painel');
  if (chkHud && chkPainel) {
    const sincroniza = v => { chkHud.checked = v; chkPainel.checked = v; };
    chkHud.onchange = () => { sincroniza(chkHud.checked); if (estadoVista === 'comparacao') setVista('comparacao'); };
    chkPainel.onchange = () => { sincroniza(chkPainel.checked); if (estadoVista === 'comparacao') setVista('comparacao'); };
  }
  if (!Object.keys(DATA.itens2d || {}).length) {
    const lr = document.getElementById('linha-real'); if (lr) lr.style.display = 'none';
    const bc = document.querySelector('[data-vista="comparacao"]'); if (bc) bc.style.display = 'none';
  }
}

function carregaArma(chave) {
  armaAtual = chave;
  const eItem = !!DATA.itens2d[chave];
  if (eItem) gruposAnim = []; // munição não tem articulações próprias (ainda)
  else constroiArma(chave);
  const arma = eItem ? DATA.itens2d[chave] : DATA.weapons[chave];
  // sliders das articulações
  const painel = document.getElementById('grupos');
  painel.innerHTML = '';
  gruposAnim.forEach(g => {
    const linha = document.createElement('div'); linha.className = 'grupo';
    const lab = document.createElement('label'); lab.textContent = g.cfg.label;
    const inp = document.createElement('input');
    inp.type = 'range';
    inp.min = g.cfg.range[0]; inp.max = g.cfg.range[1]; inp.step = 0.5; inp.value = g.default;
    inp.oninput = () => { g.valor = parseFloat(inp.value); aplicaGrupo(g.cfg, g.obj, g.valor); };
    aplicaGrupo(g.cfg, g.obj, g.valor);
    linha.appendChild(lab); linha.appendChild(inp);
    painel.appendChild(linha);
  });
  // estatísticas
  const s = arma.stats;
  const tipo = eItem ? 'Munição — voxel do sprite ' + s.tex + ' (fiel ao item/generated)'
    : (arma.kind === 'arma' ? 'Arma 3D — modelo real do jogo' : 'Modelo 3D de outro item');
  document.getElementById('stats').innerHTML =
    '<b>' + arma.label + '</b><br>' +
    tipo + '<br>' +
    'Elements: ' + s.elements + '<br>' +
    'Comprimento: ' + s.comprimento + ' units<br>' +
    'Altura: ' + s.altura + ' units · Largura: ' + s.largura + ' units<br>' +
    (arma.comprimentoRealMm ? 'Proporção real: ' + Math.round(arma.comprimentoRealMm) + ' mm<br>' : '') +
    'Textura: ' + s.tex + ' · Atlas MC 16u<br>' +
    'BBox: [' + s.bbox_min + '] → [' + s.bbox_max + ']';
  const btnComp = document.querySelector('[data-vista="comparacao"]');
  if (btnComp) btnComp.classList.toggle('com-municao', !eItem && !arma.municao);
  setVista(eItem ? 'comparacao' : 'perspectiva');
}

function inicia() {
  const canvasHost = document.getElementById('cena');
  renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
  renderer.setSize(canvasHost.clientWidth, canvasHost.clientHeight);
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  canvasHost.appendChild(renderer.domElement);

  scene = new THREE.Scene();
  scene.background = new THREE.Color(0x0e1216);
  camera = new THREE.PerspectiveCamera(45, canvasHost.clientWidth / canvasHost.clientHeight, 0.01, 100);
  scene.add(camera);

  // iluminação de estúdio
  scene.add(new THREE.HemisphereLight(0x9db4c8, 0x2a2622, 0.75));
  const chave = new THREE.DirectionalLight(0xfff1de, 2.2);
  chave.position.set(4, 7, 5); chave.castShadow = true;
  chave.shadow.mapSize.set(2048, 2048);
  chave.shadow.camera.left = -3; chave.shadow.camera.right = 3;
  chave.shadow.camera.top = 3; chave.shadow.camera.bottom = -3;
  chave.shadow.camera.far = 30; chave.shadow.bias = -0.0005;
  scene.add(chave);
  const fill = new THREE.DirectionalLight(0x86a8ff, 0.8); fill.position.set(-6, 3.5, -2); scene.add(fill);
  const rim = new THREE.DirectionalLight(0xffffff, 1.1); rim.position.set(-1, 5, -7); scene.add(rim);

  // piso neutro + grade técnica discreta
  terreno = new THREE.Mesh(
    new THREE.PlaneGeometry(60, 60),
    new THREE.MeshStandardMaterial({ color: 0x151a20, roughness: 0.95, metalness: 0 })
  );
  terreno.rotation.x = -Math.PI / 2; terreno.receiveShadow = true;
  scene.add(terreno);
  grade = new THREE.GridHelper(24, 24, 0x33414f, 0x1d252d);
  grade.position.y = 0.002; scene.add(grade);

  // boneco de proporção (3ª pessoa) — de pé, olhando para -Z
  boneco = new THREE.Group();
  const matCorpo = new THREE.MeshStandardMaterial({ color: 0x8f9aa6, roughness: 0.9 });
  const caixa = (w, h, d, x, y, z) => {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), matCorpo);
    m.position.set(x, y, z); m.castShadow = true; return m;
  };
  boneco.add(caixa(0.5, 0.5, 0.25, 0, 1.55, 0));
  boneco.add(caixa(0.5, 0.7, 0.26, 0, 0.95, 0));
  boneco.add(caixa(0.22, 0.7, 0.24, -0.14, 0.35, 0));
  boneco.add(caixa(0.22, 0.7, 0.24, 0.14, 0.35, 0));
  boneco.add(caixa(0.2, 0.6, 0.22, -0.4, 1.0, 0.08));
  boneco.add(caixa(0.2, 0.6, 0.22, 0.4, 1.0, 0.08));
  boneco.visible = false; scene.add(boneco);

  constroiItens2d();
  orbit = criaOrbit();
  montaUI();
  carregaArma(ARMA_INICIAL);

  window.addEventListener('resize', () => {
    const w = canvasHost.clientWidth, h = canvasHost.clientHeight;
    renderer.setSize(w, h); camera.aspect = w / h; camera.updateProjectionMatrix();
  });

  (function laco() {
    requestAnimationFrame(laco);
    if (rotacionado) gruposAnim.forEach(g => aplicaGrupo(g.cfg, g.obj, g.valor));
    if (estadoVista !== 'primeira' && estadoVista !== 'terceira') orbit.atualiza();
    renderer.render(scene, camera);
  })();
}

/* Scripts no fim do <body>: DOM já parseado na maioria dos casos — inicia
   imediatamente; se ainda estiver carregando, aguarda o evento. */
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', inicia);
else inicia();
"""

CSS = r"""
:root { --bg:#0e1216; --painel:#141b22e6; --borda:#26313c; --texto:#cfd8e3; --destaque:#e8b64c; }
* { box-sizing:border-box; margin:0; padding:0; }
html,body { height:100%; background:var(--bg); color:var(--texto);
  font-family:'Segoe UI',system-ui,sans-serif; overflow:hidden; }
#app { display:flex; height:100vh; }
#cena { flex:1; position:relative; }
#cena canvas { display:block; }
#painel { width:320px; background:var(--painel); border-left:1px solid var(--borda);
  padding:14px; overflow-y:auto; backdrop-filter:blur(6px); }
h1 { font-size:15px; letter-spacing:.4px; color:var(--destaque); margin-bottom:2px; }
.sub { font-size:11px; color:#7c8b9c; margin-bottom:12px; }
.secao { font-size:10px; text-transform:uppercase; letter-spacing:1.2px;
  color:#7c8b9c; margin:14px 0 6px; }
#armas { display:flex; gap:6px; flex-wrap:wrap; }
#vistas { display:flex; gap:5px; flex-wrap:wrap; }
button { background:#1b2530; color:var(--texto); border:1px solid var(--borda);
  border-radius:6px; padding:6px 10px; font-size:12px; cursor:pointer; transition:.15s; }
button:hover { border-color:var(--destaque); color:#fff; }
button.ativo { background:var(--destaque); border-color:var(--destaque); color:#1a1206; font-weight:600; }
.grupo { margin:8px 0; }
.grupo label { display:block; font-size:11.5px; margin-bottom:3px; color:#aebbc9; }
.grupo input { width:100%; accent-color:var(--destaque); }
#stats { font-size:12px; line-height:1.65; background:#0f151b; border:1px solid var(--borda);
  border-radius:8px; padding:10px; }
#stats b { color:var(--destaque); font-size:13px; }
.check { display:flex; align-items:center; gap:7px; font-size:12px; margin:8px 0; color:#aebbc9; }
.check input { accent-color:var(--destaque); }
#flutuante { position:absolute; top:12px; left:12px; display:flex; gap:6px; }
.rodape { font-size:10.5px; color:#66768a; margin-top:14px; line-height:1.5; }
@media (max-width: 860px) { #painel { width:250px; } }
#comparacao-hud { position:absolute; top:12px; right:12px; display:none; flex-direction:column; gap:5px; align-items:flex-end;
  background:var(--painel); border:1px solid var(--borda); border-radius:8px; padding:10px 12px; }
#comparacao-hud .check { margin:0; font-size:11px; }
#escala-lbl { font-size:11px; color:var(--destaque); font-weight:600; }
#tam-lbl { font-size:11px; color:#aebbc9; }
button.so-armas, button.com-municao { opacity:.35; }
button.so-armas.ativo, button.com-municao.ativo { opacity:1; }
"""

HTML_SHELL = """<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>SNC Adventures — Weapon Studio</title>
<style>__CSS__</style>
</head>
<body>
<div id="app">
  <div id="cena">
    <div id="flutuante">
      <button id="btn-reset2" title="Reenquadrar o modelo">⌖ Enquadrar</button>
      <button id="btn-full" title="Tela cheia">⛶ Tela cheia</button>
    </div>
    <div id="comparacao-hud" style="display:none">
      <div id="escala-lbl">escala</div>
      <div id="tam-lbl"></div>
      <label class="check hud"><input type="checkbox" id="chk-real" checked> proporção real (arma × munição)</label>
    </div>
  </div>
  <div id="painel">
    <h1>SNC Adventures — Weapon Studio</h1>
    <div class="sub">Inspeção 3D com o modelo e a textura REAIS do jogo · gerado por tools/gen_estudio_armas.py</div>

    <div class="secao">Arma</div>
    <div id="armas"></div>

    <div class="secao">Vistas</div>
    <div id="vistas"></div>

    <div class="secao">Articulações (grupos e pivôs reais)</div>
    <div id="grupos"></div>

    <div class="secao">Opções</div>
    <label class="check"><input type="checkbox" id="chk-mao" checked> Mostrar mão ilustrativa na 1ª pessoa</label>
    <label class="check" id="linha-real"><input type="checkbox" id="chk-real-painel" checked> Comparação na proporção real</label>
    <button id="btn-reset" style="width:100%">↺ Reset da vista atual</button>

    <div class="secao">Estatísticas</div>
    <div id="stats"></div>

    <div class="rodape">
      Botão esquerdo: orbitar · Botão direito: pan · Roda: zoom.<br>
      Vista Primeira Pessoa usa o transform <code>firstperson_righthand</code> real do JSON.<br>
      Vista Comparação: munição voxel ao lado da arma na proporção real (1 bloco = 1 m).<br>
      Gerado em __DATA__.
    </div>
  </div>
</div>
<script>__THREE__</script>
<script>window.STUDIO_DATA = __DATA_JSON__;</script>
<script>__APP__</script>
</body>
</html>
"""


def gerar():
    dados = montar_dados()
    three_src = garantir_three()

    # sanidade: nada de "</script>" acidental dentro dos blocos embutidos
    for nome, conteudo in (("three", three_src), ("app", APP_JS)):
        if "</script" in conteudo.lower():
            raise SystemExit("Conteúdo %s contém '</script>' — embutir direto quebraria o HTML." % nome)

    html = (
        HTML_SHELL
        .replace("__CSS__", CSS)
        .replace("__DATA__", dados["geradoEm"])
        .replace("__THREE__", three_src)
        .replace("__DATA_JSON__", json.dumps(dados, ensure_ascii=False))
        .replace("__APP__", APP_JS)
    )

    # versão oficial: preview/ (fonte viva, aberta na aba Preview)
    with open(SAIDA, "w", encoding="utf-8") as f:
        f.write(html)

    # versão offline empacotada: dist/ (single-file para compartilhar)
    os.makedirs(os.path.dirname(SAIDA_DIST), exist_ok=True)
    with open(SAIDA_DIST, "w", encoding="utf-8") as f:
        f.write(html)
    sha = hashlib.sha256(open(SAIDA_DIST, "rb").read()).hexdigest()
    caminho_sha = os.path.splitext(SAIDA_DIST)[0] + ".sha256"
    with open(caminho_sha, "w", encoding="utf-8") as f:
        f.write("%s  %s\n" % (sha, os.path.basename(SAIDA_DIST)))

    total = sum(len(a["elements"]) for a in dados["weapons"].values())
    print("OK: %s (%d armas, %d elements no total)" % (
        os.path.relpath(SAIDA, RAIZ), len(dados["weapons"]), total))
    for chave, arma in dados["weapons"].items():
        s = arma["stats"]
        print("  - [%s] %s: %d elements, comprimento %.1fu, textura %s, %d grupos articuláveis" % (
            arma["kind"], arma["label"], s["elements"], s["comprimento"], s["tex"], len(arma["groups"])))
    for chave, item in dados["itens2d"].items():
        s = item["stats"]
        print("  - [municao] %s: %d elements (voxel do sprite %s)" % (
            item["label"], s["elements"], s["tex"]))
    print("OK: %s (%.1f MB, sha256 %s…)" % (
        os.path.relpath(SAIDA_DIST, RAIZ), os.path.getsize(SAIDA_DIST) / 1e6, sha[:16]))


if __name__ == "__main__":
    gerar()
