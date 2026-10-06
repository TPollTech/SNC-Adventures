"""Validador de worldgen do mod: impede que um JSON quebrado trave a criacao de mundo.

Motivacao: o template_pool chegou a referenciar "minecraft:none" (processor que nao
existe), derrubando o registry worldgen/processor_list inteiro e travando o jogo em
"Preparing for world creation". Este script varre os JSONs de worldgen + receitas do
mod e confere TODAS as referencias contra o jar vanilla mapeado (fonte da verdade),
alem de validar o NBT da estrutura byte a byte.

O que e validado:
  - Referencias de registro (start_pool -> template_pool, placed -> feature,
    structure_set -> structure, processors) existem no vanilla ou no proprio mod
  - feature type / placement type / element_type existem (coletados dos JSONs vanilla)
  - Blocos e blockstates (id[prop=valor]) existem; propriedades/valores conferem
    com o blockstate do jar (variants E multipart); tags de bloco existem
  - Estrutura NBT: parse byte a byte, DataVersion bate com version.json, paleta
    valida, total de blocks = x*y*z, indices dentro da paleta, template citado
    pelo pool existe

Uso:  python tools/validate_worldgen.py
Saida: exit 0 se tudo ok; exit 1 listando os erros (bloqueia o build).
"""
import gzip
import json
import os
import re
import struct
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
GRADLE_CACHE = os.path.join(os.environ.get("USERPROFILE", ""), ".gradle", "caches", "fabric-loom")

erros = []


def erro(msg):
    erros.append(msg)


# ============================================================ jar vanilla
def achar_jar_vanilla():
    """Usa a versão configurada e um JAR com dados completos, nunca client-only."""
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as props:
        match = re.search(r"^minecraft_version\s*=\s*(\S+)\s*$", props.read(), re.M)
    if not match:
        raise ValueError("minecraft_version ausente em gradle.properties")
    versao = match.group(1)
    candidatos = []
    maven = os.path.join(GRADLE_CACHE, "minecraftMaven", "net", "minecraft")
    if not os.path.isdir(maven):
        return None
    for raiz, _, arquivos in os.walk(maven):
        for a in arquivos:
            if a.endswith(".jar") and "deobf" in a and "sources" not in a:
                cheio = os.path.join(raiz, a)
                # Outros projetos populam este cache com versões distintas e
                # artefatos sem recursos. A data do arquivo não prova compatibilidade.
                with zipfile.ZipFile(cheio) as jar:
                    nomes = set(jar.namelist())
                    obrigatorios = {"version.json", "data/minecraft/structure/igloo/top.nbt",
                                    "assets/minecraft/blockstates/dirt.json"}
                    if not obrigatorios <= nomes:
                        continue
                    if json.loads(jar.read("version.json")).get("id") != versao:
                        continue
                candidatos.append((os.path.getmtime(cheio), cheio))
    if not candidatos:
        return None
    return max(candidatos)[1]


JAR = achar_jar_vanilla()
if JAR is None:
    print("ERRO: jar vanilla (minecraft-merged-deobf) nao encontrado no cache do Loom.")
    print("      Rode o build do mod uma vez antes (gradlew build) pra popular o cache.")
    sys.exit(2)

z = zipfile.ZipFile(JAR)


# ============================================================ referencias do vanilla
DATA_VERSION = None
VANILLA_WORLDGEN_IDS = {}       # "worldgen/structure" -> {"minecraft:igloo", ...}
VANILLA_BLOCK_TAGS = set()      # {"minecraft:dirt", ...}
VANILLA_BIOME_TAGS = set()      # {"minecraft:has_structure/igloo", ...}
VANILLA_BLOCKSTATES = {}        # "minecraft:poppy" -> {"age": {"0","1",...}}
VANILLA_FEATURE_TYPES = set()   # coletados dos JSONs vanilla
VANILLA_PLACEMENT_TYPES = set()
VANILLA_POOL_ELEMENT_TYPES = set()
VANILLA_RECIPE_TYPES = set()


def carregar_vanilla():
    global DATA_VERSION, VANILLA_BLOCK_TAGS, VANILLA_BIOME_TAGS
    global VANILLA_BLOCKSTATES, VANILLA_FEATURE_TYPES, VANILLA_PLACEMENT_TYPES
    global VANILLA_POOL_ELEMENT_TYPES, VANILLA_RECIPE_TYPES

    ver = None
    try:
        ver = json.loads(z.read("version.json").decode("utf-8"))
    except KeyError:
        pass
    if ver and "world_version" in ver:
        DATA_VERSION = ver["world_version"]

    nomes = z.namelist()

    # ---- ids de worldgen por prefixo
    prefixos = set()
    for n in nomes:
        m = re.match(r"^data/minecraft/(worldgen/[a-z_]+)/[a-z0-9_/]+\.json$", n)
        if m:
            prefixos.add(m.group(1))
    for prefixo in sorted(prefixos):
        ids = set()
        for n in nomes:
            m = re.match(rf"^data/minecraft/{re.escape(prefixo)}/([a-z0-9_/]+)\.json$", n)
            if m:
                ids.add("minecraft:" + m.group(1))
        VANILLA_WORLDGEN_IDS[prefixo] = ids

    # ---- tags de bloco e de bioma
    for n in nomes:
        m = re.match(r"^data/minecraft/tags/block/([a-z0-9_/]+)\.json$", n)
        if m:
            VANILLA_BLOCK_TAGS.add("minecraft:" + m.group(1))
        m = re.match(r"^data/minecraft/tags/worldgen/biome/([a-z0-9_/]+)\.json$", n)
        if m:
            VANILLA_BIOME_TAGS.add("minecraft:" + m.group(1))

    # ---- blockstates: id -> propriedade -> valores validos
    def coletar_when(when, props):
        if not isinstance(when, dict):
            return
        for k, v in when.items():
            if k in ("OR", "AND", "NOR") and isinstance(v, list):
                for sub in v:
                    coletar_when(sub, props)
            elif isinstance(v, str):
                props.setdefault(k, set()).update(v.split("|"))

    for n in nomes:
        m = re.match(r"^assets/minecraft/blockstates/([a-z0-9_]+)\.json$", n)
        if not m:
            continue
        bid = "minecraft:" + m.group(1)
        try:
            bs = json.loads(z.read(n).decode("utf-8"))
        except Exception:
            continue
        props = {}
        # variants: chaves tipo "face=floor,facing=east" (ou "" pra sem props)
        for chave in bs.get("variants", {}).keys():
            if not chave:
                continue
            for par in chave.split(","):
                if "=" in par:
                    k, v = par.split("=", 1)
                    props.setdefault(k, set()).add(v)
        # multipart: "when" dentro de cada entrada
        for entrada in bs.get("multipart", []):
            coletar_when(entrada.get("when", {}), props)
        VANILLA_BLOCKSTATES[bid] = props

    # ---- tipos: coleta dos proprios JSONs vanilla (fonte da verdade)
    tipos_feature, tipos_placement = set(), set()
    tipos_pool, tipos_receita = set(), set()
    for n in nomes:
        if n.startswith("data/minecraft/worldgen/") and n.endswith(".json"):
            try:
                dado = json.loads(z.read(n).decode("utf-8"))
            except Exception:
                continue
            if not isinstance(dado, dict):
                continue
            if n.startswith("data/minecraft/worldgen/feature/"):
                t = dado.get("type")
                if isinstance(t, str):
                    tipos_feature.add(t)
            elif n.startswith("data/minecraft/worldgen/placed_feature/"):
                for p in dado.get("placement", []):
                    if isinstance(p, dict) and isinstance(p.get("type"), str):
                        tipos_placement.add(p["type"])
            elif n.startswith("data/minecraft/worldgen/template_pool/"):
                for el in dado.get("elements", []):
                    et = el.get("element", {}).get("element_type")
                    if isinstance(et, str):
                        tipos_pool.add(et)
        elif n.startswith("data/minecraft/recipe/") and n.endswith(".json"):
            try:
                r = json.loads(z.read(n).decode("utf-8"))
            except Exception:
                continue
            if isinstance(r.get("type"), str):
                tipos_receita.add(r["type"])

    VANILLA_FEATURE_TYPES = tipos_feature
    VANILLA_PLACEMENT_TYPES = tipos_placement
    VANILLA_POOL_ELEMENT_TYPES = tipos_pool
    VANILLA_RECIPE_TYPES = tipos_receita


carregar_vanilla()


# ============================================================ arquivos do mod
MOD_JSONS = {}   # "intoxicantes/worldgen/structure/x.json" -> dict
MOD_IDS = {}     # prefixo ("worldgen/structure") -> {"intoxicantes:x"}
MOD_NBTS = set() # {"intoxicantes/structure/mercado_gago.nbt", ...}

# prefixos de registro que o mod usa (ordem mais-longa-primeiro: structure_set
# antes de structure, senao o id sai errado)
PREFIXOS_MOD = ["worldgen/template_pool", "worldgen/placed_feature", "worldgen/structure_set",
                "worldgen/structure", "worldgen/feature", "recipe", "loot_table"]


def carregar_mod():
    for raiz, _, arquivos in os.walk(DATA):
        for a in arquivos:
            cheio = os.path.join(raiz, a)
            rel = os.path.relpath(cheio, DATA).replace("\\", "/")
            if a.endswith(".nbt"):
                MOD_NBTS.add(rel)
                continue
            if not a.endswith(".json"):
                continue
            try:
                with open(cheio, encoding="utf-8") as f:
                    dado = json.load(f)
            except Exception as e:
                erro(f"JSON invalido: {rel} ({e})")
                continue
            MOD_JSONS[rel] = dado
            for prefixo in sorted(PREFIXOS_MOD, key=len, reverse=True):
                cabeca = f"intoxicantes/{prefixo}/"
                if rel.startswith(cabeca) and rel.endswith(".json"):
                    MOD_IDS.setdefault(prefixo, set()).add(
                        "intoxicantes:" + rel[len(cabeca):-len(".json")])
                    break


carregar_mod()


def carregar_feature_types_java():
    """Features registradas EM JAVA (registrarFeature ou FEATURE_TYPE direto)
    sao invisiveis pra varredura de JSONs — coleta dos .java do mod.
    (ex: intoxicantes:coqueiro, registrada via MapCodec no IntoxicantesMod)"""
    extras = set()
    src = os.path.join(ROOT, "src", "main", "java")
    for raiz, _, arquivos in os.walk(src):
        for a in arquivos:
            if not a.endswith(".java"):
                continue
            try:
                txt = open(os.path.join(raiz, a), encoding="utf-8").read()
            except Exception:
                continue
            extras.update(re.findall(r'registrarFeature\(\s*"([a-z0-9_/]+)"', txt))
            # registro direto: BuiltInRegistries.FEATURE_TYPE ... fromNamespaceAndPath(MOD_ID, "nome")
            for m in re.finditer(
                    r'BuiltInRegistries\.FEATURE_TYPE.{0,400}?fromNamespaceAndPath\(MOD_ID,\s*"([a-z0-9_/]+)"',
                    txt, re.S):
                extras.add(m.group(1))
    return {"intoxicantes:" + n for n in extras}


FEATURE_TYPES_MOD = carregar_feature_types_java()


# ============================================================ helpers
def ref_valida(ref, prefixo):
    """'ns:caminho' existe como data/<ns>/<prefixo>/<caminho>.json (vanilla ou mod)?"""
    if ":" not in ref:
        return False
    ns, caminho = ref.split(":", 1)
    if ns == "intoxicantes":
        return ref in MOD_IDS.get(prefixo, set())
    if ns == "minecraft":
        return ref in VANILLA_WORLDGEN_IDS.get(prefixo, set())
    return True  # outro mod: fora do escopo deste validador


def checar_bloco_estado(ref, onde):
    """'minecraft:poppy', 'minecraft:slab[type=bottom]', '#minecraft:dirt'."""
    if ref.startswith("#"):
        if ref[1:] not in VANILLA_BLOCK_TAGS:
            erro(f"{onde}: tag de bloco inexistente no vanilla: {ref}")
        return
    if ":" not in ref:
        erro(f"{onde}: id de bloco sem namespace: {ref}")
        return
    ns, caminho = ref.split(":", 1)
    base, _, props_txt = caminho.partition("[")
    bid = f"{ns}:{base}"
    if ns == "minecraft" and bid not in VANILLA_BLOCKSTATES:
        erro(f"{onde}: bloco inexistente no vanilla: {bid}")
        return
    if ns != "minecraft":
        return
    props = {}
    if props_txt:
        if not props_txt.endswith("]"):
            erro(f"{onde}: blockstate malformado: {ref}")
            return
        for par in props_txt[:-1].split(","):
            if "=" not in par:
                erro(f"{onde}: propriedade malformada em {ref}")
                return
            k, v = par.split("=", 1)
            props[k] = v
    validas = VANILLA_BLOCKSTATES.get(bid, {})
    for k, v in props.items():
        if k not in validas:
            erro(f"{onde}: propriedade '{k}' nao existe em {bid} (validas: {sorted(validas)})")
        elif v not in validas[k]:
            erro(f"{onde}: valor '{v}' invalido pra '{k}' de {bid} (validos: {sorted(validas[k])})")


# ============================================================ validacoes por tipo
def validar_template_pool(rel, pool):
    for i, el in enumerate(pool.get("elements", [])):
        e = el.get("element", {})
        et = e.get("element_type", "")
        if et and et not in VANILLA_POOL_ELEMENT_TYPES:
            erro(f"{rel}[{i}]: element_type inexistente no vanilla: {et} "
                 f"(existentes: {sorted(VANILLA_POOL_ELEMENT_TYPES)})")
        proc = e.get("processors")
        if isinstance(proc, str):
            if not ref_valida(proc, "worldgen/processor_list"):
                erro(f"{rel}[{i}]: processors referencia inexistente: {proc} "
                     f"(isso TRAVA a criacao de mundo! use {{\"processors\": []}} inline)")
        loc = e.get("location")
        if isinstance(loc, str) and ":" in loc:
            ns, caminho = loc.split(":", 1)
            chave = f"{ns}/structure/{caminho}.nbt"
            if ns == "intoxicantes" and chave not in MOD_NBTS:
                erro(f"{rel}[{i}]: template nao existe no mod: {loc} "
                     f"(esperado data/{chave})")


def validar_structure(rel, s):
    biomas = s.get("biomes", "")
    if isinstance(biomas, str) and biomas.startswith("#"):
        ref = biomas[1:]
        if ref in VANILLA_BIOME_TAGS:
            pass
        elif ref.startswith("intoxicantes:"):
            # tag de bioma DO PROPRIO MOD: o arquivo precisa existir
            # (data/intoxicantes/tags/worldgen/biome/<caminho>.json)
            caminho_tag = os.path.join(DATA, "intoxicantes", "tags", "worldgen",
                                       "biome", ref.split(":", 1)[1] + ".json")
            if not os.path.exists(caminho_tag):
                erro(f"{rel}: tag de bioma do mod sem arquivo: {biomas} "
                     f"(esperado {caminho_tag})")
        else:
            erro(f"{rel}: tag de bioma inexistente no vanilla: {biomas}")
    pool = s.get("start_pool", "")
    if isinstance(pool, str) and ":" in pool and not ref_valida(pool, "worldgen/template_pool"):
        erro(f"{rel}: start_pool nao existe (vanilla nem mod): {pool}")
    hm = s.get("project_start_to_heightmap")
    validas_hm = {"WORLD_SURFACE_WG", "WORLD_SURFACE", "OCEAN_FLOOR_WG", "OCEAN_FLOOR",
                  "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES"}
    if hm is not None and hm not in validas_hm:
        erro(f"{rel}: heightmap desconhecida: {hm}")


def validar_placed(rel, pf):
    f = pf.get("feature", "")
    if isinstance(f, str) and ":" in f and not ref_valida(f, "worldgen/feature"):
        erro(f"{rel}: feature referenciada nao existe (vanilla nem mod): {f}")
    for i, p in enumerate(pf.get("placement", [])):
        t = p.get("type", "")
        if t and t not in VANILLA_PLACEMENT_TYPES:
            erro(f"{rel}[{i}]: placement type inexistente: {t} "
                 f"(existentes: {sorted(VANILLA_PLACEMENT_TYPES)})")
        if t == "minecraft:block_predicate_filter":
            _checar_predicado(rel, i, p.get("predicate", {}))
        if t == "minecraft:heightmap":
            hm = p.get("heightmap", "")
            validas = {"WORLD_SURFACE_WG", "WORLD_SURFACE", "OCEAN_FLOOR_WG", "OCEAN_FLOOR",
                       "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES"}
            if hm not in validas:
                erro(f"{rel}[{i}]: heightmap desconhecida: {hm}")


def _checar_predicado(rel, i, pred):
    if not isinstance(pred, dict):
        return
    t = pred.get("type", "")
    if "blocks" in pred:
        b = pred["blocks"]
        alvo = b if isinstance(b, list) else [b]
        for ref in alvo:
            checar_bloco_estado(ref, f"{rel}[{i}] predicate({t})")
    if isinstance(pred.get("tag"), str):
        tag = pred["tag"]
        checar_bloco_estado(tag if tag.startswith("#") else "#" + tag, f"{rel}[{i}] predicate({t})")
    for k in ("all_of", "any_of", "none_of"):
        for sub in pred.get(k, []):
            _checar_predicado(rel, i, sub)


def validar_feature(rel, feat):
    t = feat.get("type", "")
    if t and t not in VANILLA_FEATURE_TYPES and t not in FEATURE_TYPES_MOD:
        erro(f"{rel}: feature type inexistente: {t} "
             f"(existentes: {len(VANILLA_FEATURE_TYPES)} tipos, ex: {sorted(VANILLA_FEATURE_TYPES)[:4]})")
    # simple_block / random_patch: estados de bloco
    for chave in ("to_place", "state", "config"):
        cfg = feat.get(chave)
        if isinstance(cfg, dict):
            state = cfg.get("state")
            if isinstance(state, dict) and isinstance(state.get("id"), str):
                props = state.get("properties", {})
                sufixo = "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]" if props else ""
                checar_bloco_estado(state["id"] + sufixo, f"{rel} {chave}")


def validar_receita(rel, r):
    t = r.get("type", "")
    if t and t not in VANILLA_RECIPE_TYPES:
        print(f"  aviso: {rel}: recipe type nao encontrado nos JSONs vanilla: {t}")


def validar_nbt_mercado():
    nbt = os.path.join(DATA, "intoxicantes", "structure", "mercado_gago.nbt")
    if not os.path.exists(nbt):
        erro("structure/mercado_gago.nbt nao existe (o template_pool precisa dele)")
        return
    raw = gzip.decompress(open(nbt, "rb").read())

    # ---- parser NBT completo (13 tipos), byte a byte, formato 26.3
    class Fail(Exception):
        pass

    class R:
        def __init__(self, b):
            self.b, self.i = b, 0

        def need(self, k):
            if self.i + k > len(self.b):
                raise Fail(f"EOF: pedindo {k} em {self.i}/{len(self.b)}")
            v = self.b[self.i:self.i + k]
            self.i += k
            return v

        def u1(self):
            return self.need(1)[0]

        def i2(self):
            return struct.unpack(">H", self.need(2))[0]

        def i4(self):
            return struct.unpack(">i", self.need(4))[0]

        def st(self):
            n = self.i2()
            if n > 1_000_000:
                raise Fail(f"string absurda ({n}) em {self.i}")
            return self.need(n).decode("utf-8", "replace")

        def val(self, t):
            if t == 1:
                return self.need(1)[0]
            if t == 2:
                return self.i2()
            if t == 3:
                return self.i4()
            if t == 4:
                return struct.unpack(">q", self.need(8))[0]
            if t == 5:
                return struct.unpack(">f", self.need(4))[0]
            if t == 6:
                return struct.unpack(">d", self.need(8))[0]
            if t == 7:
                k = self.i4()
                if k < 0:
                    raise Fail("bytearray negativo")
                return self.need(k)
            if t == 8:
                return self.st()
            if t == 9:
                it = self.u1()
                k = self.i4()
                if it == 0:
                    return []  # lista vazia de tipo 0 (fim) e permitida
                if k < 0 or k > 5_000_000:
                    raise Fail(f"lista len {k}")
                return [self.val(it) for _ in range(k)]
            if t == 10:
                d = {}
                while True:
                    tt = self.u1()
                    if tt == 0:
                        return d
                    # IMPORTANTE: nome ANTES do payload — em Python,
                    # d[self.st()] = self.val(tt) avaliaria o RHS (payload)
                    # PRIMEIRO e dessincronizaria o stream inteiro!
                    nm = self.st()
                    d[nm] = self.val(tt)
            if t == 11:
                k = self.i4()
                if k < 0 or k > 10_000_000:
                    raise Fail(f"intarray len {k}")
                return list(struct.unpack(f">{k}i", self.need(4 * k)))
            if t == 12:
                k = self.i4()
                if k < 0 or k > 10_000_000:
                    raise Fail(f"longarray len {k}")
                return list(struct.unpack(f">{k}q", self.need(8 * k)))
            raise Fail(f"tag NBT {t} inesperada em {self.i}")

    try:
        r = R(raw)
        t = r.u1()
        if t != 10:
            erro("mercado_gago.nbt: raiz nao e TAG_Compound")
            return
        r.st()
        d = r.val(10)
        if r.i != len(raw):
            erro(f"mercado_gago.nbt: {len(raw) - r.i} byte(s) sobrando no fim (NBT malformado)")
    except Fail as f:
        erro(f"mercado_gago.nbt: NBT malformado ({f})")
        return

    # ---- AUTO-TESTE do parser: tem que parsear um template vanilla conhecido.
    # Se isso falhar, o parser esta quebrado (nao o arquivo do mod!).
    try:
        igloo_raw = gzip.decompress(z.read("data/minecraft/structure/igloo/top.nbt"))
        ri = R(igloo_raw)
        assert ri.u1() == 10
        ri.st()
        ig = ri.val(10)
        if not (isinstance(ig.get("blocks"), list) and len(ig["blocks"]) > 0):
            erro("AUTO-TESTE: parser NBT nao consegue ler o igloo/top.nbt vanilla "
                 "(parser quebrado — nao confie nos resultados dele)")
    except Exception as e:
        erro(f"AUTO-TESTE: parser NBT falhou no igloo vanilla: {e}")

    if DATA_VERSION and d.get("DataVersion") != DATA_VERSION:
        erro(f"mercado_gago.nbt: DataVersion {d.get('DataVersion')} != vanilla {DATA_VERSION} "
             f"(template de outra versao pode corromper mundos)")

    # ---- formato 26.3: size = TAG_List de 3 ints; blocks/palette = TAG_List de compounds
    size = d.get("size")
    if not (isinstance(size, list) and len(size) == 3):
        erro("mercado_gago.nbt: 'size' deveria ser TAG_List de 3 ints (formato 26.3)")
        return
    sx, sy, sz = size
    total = sx * sy * sz

    paleta = d.get("palette")
    blocks = d.get("blocks")
    if not isinstance(paleta, list) or not isinstance(blocks, list):
        erro("mercado_gago.nbt: 'blocks' e 'palette' deveriam ser TAG_List de compounds (formato 26.3)")
        return
    if len(blocks) == 0:
        erro("mercado_gago.nbt: nenhum bloco no template (estrutura vazia)")
    if len(paleta) < 2:
        erro(f"mercado_gago.nbt: paleta com {len(paleta)} entradas (menos que 2 = estrutura vazia ou placeholder)")

    vistos = set()
    for e in blocks:
        if not isinstance(e, dict) or "pos" not in e or "state" not in e:
            erro("mercado_gago.nbt: entrada de bloco sem 'pos'/'state'")
            break
        pos = e["pos"]
        if not (isinstance(pos, list) and len(pos) == 3):
            erro("mercado_gago.nbt: 'pos' deveria ser lista de 3 ints")
            break
        px, py, pz = pos
        if not (0 <= px < sx and 0 <= py < sy and 0 <= pz < sz):
            erro(f"mercado_gago.nbt: pos {pos} fora do tamanho {size}")
            break
        if not isinstance(e["state"], int) or not (0 <= e["state"] < len(paleta)):
            erro(f"mercado_gago.nbt: state {e.get('state')} fora da paleta (tam {len(paleta)})")
            break
        vistos.add(tuple(pos))
    if len(vistos) != len(blocks):
        erro(f"mercado_gago.nbt: {len(blocks) - len(vistos)} bloco(s) duplicado(s) na mesma pos")

    # ---- NBT de tile entity: tem que ser NA ENTRADA DE BLOCO, nunca na paleta.
    # (o vanilla inteiro: 0 na paleta, ~5600 na entrada; StructureTemplate so
    # repassa o nbt da entrada pro placeInWorld — na paleta e' ignorado e a
    # placa nasce SEM TEXTO, que foi o bug do letreiro do mercado)
    for e in paleta:
        if isinstance(e, dict) and "nbt" in e:
            erro(f"mercado_gago.nbt: paleta[{e.get('id')}] tem 'nbt' — o vanilla so le "
                 "nbt da ENTRADA DE BLOCO (blocks[*].nbt); na paleta e' ignorado")
            break

    # ---- placa de parede precisa de SUPORTE: wall_sign se prende no bloco
    # ATRAS dela (oposto ao facing); sem suporte ela desanexa na geracao e o
    # letreiro vira item caindo (2o bug do letreiro)
    idx_por_pos = {tuple(b.get("pos", [0, 0, 0])): b for b in blocks if isinstance(b, dict)}

    def id_da_entrada(b):
        st = b.get("state")
        return paleta[st].get("id", "") if isinstance(st, int) and st < len(paleta) else ""

    for b in blocks:
        if not isinstance(b, dict) or "sign" not in id_da_entrada(b):
            continue
        px, py, pz = b["pos"]
        props = paleta[b["state"]].get("properties", {}) or {}
        facing = props.get("facing", "north")
        dx, dz = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}.get(
            facing, (0, -1))
        suporte = idx_por_pos.get((px - dx, py, pz - dz))
        sid = id_da_entrada(suporte) if suporte else ""
        if sid.endswith("air") or sid == "":
            erro(f"mercado_gago.nbt: wall_sign em {b['pos']} facing={facing} sem bloco de "
                 f"suporte atras ({px - dx},{py},{pz - dz}) — desanexa na geracao (use o "
                 "caractere de parede na posicao de tras)")

    for e in paleta:
        nome = e.get("id") if isinstance(e, dict) else None
        if not isinstance(nome, str) or ":" not in nome:
            erro(f"mercado_gago.nbt: paleta com id invalido: {nome!r}")
            continue
        props = e.get("properties", {}) if isinstance(e, dict) else {}
        sufixo = "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]" if props else ""
        checar_bloco_estado(nome + sufixo, "mercado_gago.nbt paleta")

    # ==================================================== v1.2.51 — ANATOMIA NOVA
    # A faixa de LED do letreiro tem que nascer COMPLETA (1 painel J + 14
    # extensões X na fileira y3 do prédio) — a 1.2.31–50 semeava só o J e o
    # texto do renderer desenhava POR CIMA da fachada errada (o "caos").
    placa = [b for b in blocks if isinstance(b, dict)
             and id_da_entrada(b).startswith("intoxicantes:placa_esquinao")]
    paineis = [b for b in placa
               if (paleta[b["state"]].get("properties", {}) or {}).get("parte") == "painel"]
    extensoes = [b for b in placa
                 if (paleta[b["state"]].get("properties", {}) or {}).get("parte") == "extensao"]
    if len(paineis) != 1:
        erro(f"mercado_gago.nbt: o letreiro deve ter EXATAMENTE 1 painel (tem {len(paineis)})")
    if len(extensoes) != 14:
        erro(f"mercado_gago.nbt: a faixa de LED deve ter 14 EXTENSOES (tem {len(extensoes)}) "
             "— 1 painel + 14 = 15 blocos de fachada; template velho semeava só o painel")
    for b in placa:
        if "nbt" in b and (paleta[b["state"]].get("properties", {}) or {}).get("parte") != "painel":
            erro(f"mercado_gago.nbt: extensao da faixa em {b['pos']} tem nbt "
                 "(só o PAINEL tem texto; nbt na extensão = texto duplicado)")
            break
    if paineis and tuple(paineis[0]["pos"]) != (13, 3, 18):
        erro(f"mercado_gago.nbt: o painel do letreiro deve estar em (13,3,18) "
             f"(centro da fachada, sobre a porta x13; veio {paineis[0]['pos']})")

    # ==================================================== v1.2.61 — ANATOMIA V2
    # O template 27x27 substituiu o v1 (15x19) no MESMO arquivo: todas as
    # asserções ancoram no CENTRO GEOMÉTRICO (célula 13,13) — a mesma base da
    # descoberta espiral, do /gagomarket rebuild e das reforms do zelador.
    def estado_em(x, y, z):
        b = idx_por_pos.get((x, y, z))
        return paleta[b["state"]].get("id", "") if b else ""

    def props_em(x, y, z):
        b = idx_por_pos.get((x, y, z))
        return (paleta[b["state"]].get("properties", {}) or {}) if b else {}

    if (sx, sy, sz) != (27, 8, 27):
        erro(f"mercado_gago.nbt: tamanho {size} — o v2 é 27x8x27 (lote 27x27, "
             "fachada z17, pátio z20..26; template velho 15x19? rode o gerador)")

    # ZELADOR: nenhuma madeira de tronco — a varredura destrói QUALQUER
    # #minecraft:logs no raio ±10 (as colunas do v1 sumiriam no 1o zelador)
    for e in paleta:
        if "_log" in e.get("id", ""):
            erro(f"mercado_gago.nbt: paleta com tronco ({e.get('id')}) — o zelador "
                 "DESTRÓI #minecraft:logs no box (use slab/quartzo/concreto)")
            break

    # Porta-grade: o vão da porta (x13, z17) deve ser a dupla do mod.
    if estado_em(13, 1, 17) != "intoxicantes:porta_grade" \
            or estado_em(13, 2, 17) != "intoxicantes:porta_grade":
        erro(f"mercado_gago.nbt: o vão da porta (13,z17) deve ser a PORTA-GRADE "
             f"do mod (lower+upper); veio '{estado_em(13, 1, 17)}'/'{estado_em(13, 2, 17)}' "
             "— a porta de spruce velha não tem guichê de madrugada")
    if props_em(13, 1, 17).get("half") != "lower" or props_em(13, 2, 17).get("half") != "upper":
        erro("mercado_gago.nbt: porta-grade sem half lower/upper em (13,1..2,17)")

    # ÂNCORAS DO JAVA em AR: centro+(0,0,3) = âncora da PORTA (PORTA_LOCAL —
    # desde a v1.2.76 o Gago não desce mais pro guichê da madrugada, mas o offset
    # segue vivo como âncora da porta e do ponto do Juça) e centro+(-3,0,-2) =
    # balcão do Gago (BALCAO_LOCAL, o plantão 24h) — bloco ali prende o
    # NPC dentro de parede (o Gago é ENTITY, não bloco)
    for ax, az, nome in ((13, 16, "âncora da porta (PORTA_LOCAL)"),
                         (10, 11, "balcão do Gago (BALCAO_LOCAL)")):
        if estado_em(ax, 1, az) != "minecraft:air":
            erro(f"mercado_gago.nbt: {nome} em ({ax},1,{az}) não é ar "
                 f"(veio {estado_em(ax, 1, az)})")

    # v1.2.75 — O CAIXA DO MERCADINHO no CENTRO do balcão (x10, z12): lower +
    # upper com facing=south (o visor olha pro corredor da porta) e o POSTO
    # de TRÁS (10,1,11) em AR — exatamente o BALCAO_LOCAL acima (o Gago do
    # gerenciador nasce no posto que o CaixaMercadoBlock espera; caixa e
    # gerenciador concordam). Também trava o DOUBLE_BLOCK: metade sozinha
    # some no primeiro updateShape.
    for cy, half in ((1, "lower"), (2, "upper")):
        if estado_em(10, cy, 12) != "intoxicantes:caixa_mercado":
            erro(f"mercado_gago.nbt: o caixa_mercado não está em (10,{cy},12) "
                 f"(veio {estado_em(10, cy, 12)!r})")
        elif props_em(10, cy, 12).get("half") != half:
            erro(f"mercado_gago.nbt: o caixa_mercado em (10,{cy},12) deve ser "
                 f"half={half} (a registradora é a peça de duas metades)")
    if estado_em(10, 1, 12) == "intoxicantes:caixa_mercado" and (
            props_em(10, 1, 12).get("facing") != "south"
            or props_em(10, 2, 12).get("facing") != "south"):
        erro("mercado_gago.nbt: o caixa_mercado deve olhar facing=south "
             "(o visor do carrinho pro corredor da porta, o Gago atrás)")
    if estado_em(10, 1, 11) != "minecraft:air":
        erro(f"mercado_gago.nbt: o POSTO do caixa (10,1,11) deve ser AR "
             f"(veio {estado_em(10, 1, 11)!r}) — é onde o Gago ancora")

    # NO-OPS DO ZELADOR (reformarPatio/reformarEntrada, mesmas contas a partir
    # do centro). v1.2.68 — PÁTIO LIMPO: o template NÃO pode mais nascer com
    # o legado bugado que o zelador recolhe (hidrante no asfalto, postes em
    # vaga, travessia cruzando o estacionamento) — senão /gagomarket rebuild
    # recria o bug que a reforma apaga.
    if estado_em(10, 1, 20) == "intoxicantes:hidrante":
        erro("mercado_gago.nbt: hidrante no MEIO do asfalto (10,1,20) — o pátio "
             "é vaga; o hidrante fica na CALÇADA (11,1,19), ao lado da travessia")
    if estado_em(11, 1, 19) != "intoxicantes:hidrante":
        erro(f"mercado_gago.nbt: o hidrante da calçada saiu de (11,1,19) "
             f"(veio {estado_em(11, 1, 19)!r})")
    for hx in (8, 18):
        if estado_em(hx, 1, 22) == "intoxicantes:hidrante":
            erro(f"mercado_gago.nbt: hidrante em vaga ({hx},1,22) — o zelador "
                 "RECOLHE hidrante sobre asfalto (o bug do hidrante em vaga 1.2.24)")
            break
    for px, pz in ((3, 20), (23, 20), (3, 23), (23, 23)):
        if estado_em(px, 1, pz) == "intoxicantes:poste_luz":
            erro(f"mercado_gago.nbt: poste DENTRO da vaga ({px},1,{pz}) — vaga com "
                 "poste no meio não estaciona; os postes ficam nos cantos do lote")
            break
    for ex, ez in ((0, 19), (26, 19)):
        if estado_em(ex, 1, ez) != "intoxicantes:poste_luz" \
                or props_em(ex, 1, ez).get("parte") != "base":
            erro(f"mercado_gago.nbt: a BASE do poste de canto não está em "
                 f"({ex},1,{ez}) (veio {estado_em(ex, 1, ez)!r})")
    for lz in range(20, 26):
        for lx in range(2, 26):
            if estado_em(lx, 1, lz) == "intoxicantes:faixa_pedestre":
                erro(f"mercado_gago.nbt: travessia no ASFALTO ({lx},1,{lz}) — o pátio "
                     "é vaga; a travessia fica SÓ na calçada (z19)")
                break
        else:
            continue
        break
    if estado_em(12, 1, 19) != "intoxicantes:faixa_pedestre" \
            or estado_em(13, 1, 19) != "intoxicantes:faixa_pedestre" \
            or estado_em(14, 1, 19) != "intoxicantes:faixa_pedestre":
        erro("mercado_gago.nbt: a travessia da CALÇADA (12..14,1,19) sumiu do "
             "template — alinhada com a porta (x13)")

    # v1.2.76 — NENHUMA "PRATELEIRA DE MENTIRA" NO SALÃO: as gôndolas de
    # quartzo/slab do v1.2.61–75 (laterais x3..4/x22..23 em z4..15 e as
    # pontas do centro) saíram — o bloco que vende é a gôndola do mod. O SLAB
    # que sobra é o BALCÃO (z12, x8..12) e o quartzo, o piso.
    for ay in (1, 2):
        for az in range(1, 17):
            for lx in range(3, 24):
                if az == 12 and 8 <= lx <= 12:
                    continue  # o BALCÃO é o único slab do salão
                estado = estado_em(lx, ay, az)
                if estado in ("minecraft:smooth_quartz",
                              "minecraft:smooth_stone_slab"):
                    erro(f"mercado_gago.nbt: {estado} sobrou no salão em "
                         f"({lx},{ay},{az}) — prateleira de imitação (o "
                         "mobiliário do salão é a gôndola que vende + o balcão)")
                    break
            else:
                continue
            break
        else:
            continue
        break
    # v1.2.68 — SALÃO LIMPO E ILUMINADO: a vitrine de vidro-BLOCO do 1.2.67
    # parecia "vidro aleatório" flutuando no salão (playtest) — NÃO volta; e
    # as 3 fileiras de LÂMPADA LED (z5/z10/z14, x5..21) cruzam o salão.
    for ay in range(1, 5):
        for az in range(1, 17):
            for lx in range(2, 26):
                if estado_em(lx, ay, az) == "minecraft:glass":
                    erro(f"mercado_gago.nbt: vidro-bloco cheio no interior "
                         f"({lx},{ay},{az}) — a vitrine das gôndolas saiu na "
                         "1.2.68 (cubo de vidro = vidro aleatório no salão)")
                    break
            else:
                continue
            break
        else:
            continue
        break
    # v1.2.78 — as lâmpadas do teto saíram de TRÊS fileiras CORRIDAS (z5/z10/
    # z14, x5..21 = 51 tubos colados) para uma MALHA de passo 3 (7 colunas x 5
    # fileiras = 35). O teste inverteu e ficou mais forte: toda célula da malha
    # tem tubo, NENHUMA fora dela, e nenhuma dupla colada — que é exatamente o
    # que segura a parede de tubo de fora.
    LAMP_X = (4, 7, 10, 13, 16, 19, 22)
    LAMP_Z = (2, 5, 8, 11, 14)
    for lz in LAMP_Z:
        for lx in LAMP_X:
            if estado_em(lx, 3, lz) != "intoxicantes:lampada_led":
                erro(f"mercado_gago.nbt: a malha de LÂMPADA LED devia ter tubo "
                     f"em ({lx},3,{lz}) (veio {estado_em(lx, 3, lz)!r}) — "
                     "teto de mercado é claro")
    for lz in range(1, 17):
        for lx in range(2, 26):
            if estado_em(lx, 3, lz) != "intoxicantes:lampada_led":
                continue
            if lx not in LAMP_X or lz not in LAMP_Z:
                erro(f"mercado_gago.nbt: LÂMPADA LED fora da malha em "
                     f"({lx},3,{lz}) — a fileira corrida voltou")
            if (estado_em(lx - 1, 3, lz) == "intoxicantes:lampada_led"
                    or estado_em(lx + 1, 3, lz) == "intoxicantes:lampada_led"):
                erro(f"mercado_gago.nbt: duas LÂMPADAS coladas em ({lx},3,{lz}) "
                     "— a parede de tubo não volta")
    if estado_em(12, 2, 18) == "intoxicantes:painel_led":
        erro("mercado_gago.nbt: painel_led em (12,2,18) dispara a reforma do "
             "display do v1 (demole a fileira x3..9) — o display fica em (8..10,2,18)")
    if estado_em(18, 1, 18) != "intoxicantes:painel_led":
        erro("mercado_gago.nbt: o mini display 'ABERTO 24H' não está em (18,1,18) "
             "(o lugar EXATO que a reforma planta; fora dali ela DUPLICA o display)")

    from gen_mercado import COLUNAS_ILHA, FILEIRAS_ILHA, secao_prateleira
    if COLUNAS_ILHA != (6, 8, 10, 16, 18, 20) or FILEIRAS_ILHA != (6, 9):
        erro('Layout das gôndolas diverge do respiro aprovado: x6/8/10/16/18/20, z6/9')
    total_gondolas = sum(id_da_entrada(b) == 'intoxicantes:prateleira_mercado' for b in blocks)
    if total_gondolas != 24:
        erro(f'mercado_gago.nbt: esperado 24 blocos de gôndola, veio {total_gondolas}')
    # v1.2.79 — 24 gôndolas com vãos laterais e fileiras separadas: z6/z9.
    # Cada face tem seu corredor, expondo os 18 slots de cada bloco sem
    # esconder produtos em costas-com-costas.
    for px in COLUNAS_ILHA:
        for pz in FILEIRAS_ILHA:
            if estado_em(px, 1, pz) != "intoxicantes:prateleira_mercado":
                erro(f"mercado_gago.nbt: a BASE da prateleira de venda não está "
                     f"em ({px},1,{pz}) (veio {estado_em(px, 1, pz)!r})")
            esperado = "north" if pz == FILEIRAS_ILHA[0] else "south"
            if props_em(px, 1, pz).get("facing") != esperado:
                erro(f"mercado_gago.nbt: a prateleira em ({px},1,{pz}) deve olhar {esperado} para o corredor")
            if estado_em(px, 2, pz) != "intoxicantes:prateleira_mercado":
                erro(f"mercado_gago.nbt: a prateleira em ({px},1,{pz}) está sem "
                     f"CORPO em ({px},2,{pz}) (a gôndola é alta, 2 blocos)")
    # nasce ABASTECIDA: o estoque de partida (Items no codec 26.3) + a tabela
    # de preços (a etiqueta) vão no NBT de bloco de CADA gôndola
    def prateleira_nbt_em(x, y, z):
        b = idx_por_pos.get((x, y, z))
        if b and id_da_entrada(b) == "intoxicantes:prateleira_mercado" and isinstance(b.get("nbt"), dict):
            return b["nbt"]
        return None

    from gen_mercado import catalogo_prateleira, agenda_prateleira
    produtos_expostos = set()
    secoes_expostas = set()
    for px, py, pz in ((x, y, z) for x in COLUNAS_ILHA
                       for y in (1, 2) for z in FILEIRAS_ILHA):
        entrada = idx_por_pos.get((px, py, pz))
        estado = paleta[entrada["state"]] if entrada else {}
        facing_esperado = "north" if pz == FILEIRAS_ILHA[0] else "south"
        if estado.get("properties", {}).get("facing") != facing_esperado:
            erro(f"mercado_gago.nbt: prateleira ({px},{py},{pz}) deve olhar {facing_esperado} "
                 "para abrir frente e fundo aos corredores separados")
        nbt_p = prateleira_nbt_em(px, py, pz)
        if nbt_p is None:
            erro(f"mercado_gago.nbt: a prateleira em ({px},{py},{pz}) nasceu sem NBT "
                 "(o estoque de partida do Gago vai no NBT de bloco)")
        else:
            # hierarquia da BE: prateleira → itens → Items (o ContainerHelper
            # lê 'Items' DENTRO do child 'itens' — lista solta na raiz = prateleira vazia)
            raiz_p = nbt_p.get("prateleira", {})
            secoes_expostas.add(raiz_p.get("secao"))
            itens_p = raiz_p.get("itens", {}).get("Items", []) if isinstance(raiz_p.get("itens"), dict) else []
            # As duas faces nascem abastecidas com a seção alternada nos slots 9..17.
            esperado_slots = 18
            if not isinstance(itens_p, list) or len(itens_p) != esperado_slots:
                erro(f"mercado_gago.nbt: a prateleira em ({px},{py},{pz}) nasceu com "
                     f"{len(itens_p) if isinstance(itens_p, list) else 0} itens "
                     f"no estoque de partida (esperado: {esperado_slots} slots abastecidos)")
            else:
                produtos_expostos.update(item.get("id") for item in itens_p)
                slots = [item.get("Slot") for item in itens_p]
                if not all(isinstance(slot, int) for slot in slots) or sorted(slots) != list(range(esperado_slots)):
                    erro(f"mercado_gago.nbt: slots repetidos/ausentes na prateleira ({px},{py},{pz})")
                secao = raiz_p.get("secao")
                if not isinstance(secao, int):
                    erro(f"mercado_gago.nbt: seção inválida em ({px},{py},{pz})")
                    continue
                secao_normalizada, agenda = agenda_prateleira(secao)
                if secao != agenda_prateleira(secao_prateleira(px, py, pz))[0]:
                    erro(f'mercado_gago.nbt: seção do layout divergente em ({px},{py},{pz})')
                if secao_normalizada != secao:
                    erro(f"mercado_gago.nbt: seção fora do catálogo em ({px},{py},{pz})")
                if not all(k in raiz_p for k in ("precos", "doses", "reposicao")):
                    erro(f"mercado_gago.nbt: prateleira ({px},{py},{pz}) sem preços/doses/reposição")
                for item in itens_p:
                    slot = str(item.get("Slot"))
                    if raiz_p.get("precos", {}).get(slot, 0) <= 0 or raiz_p.get("doses", {}).get(slot, 0) <= 0:
                        erro(f"mercado_gago.nbt: item sem preço/dose na prateleira ({px},{py},{pz})")
                    indice = item.get("Slot")
                    if not isinstance(indice, int) or not 0 <= indice < esperado_slots:
                        continue
                    if indice >= 9:
                        # o lado de trás da ilha: seção ALTERNADA (v1.2.75)
                        _s, agenda_fundo = agenda_prateleira(secao + 1)
                        id_, produto, dose, preco, _stock = agenda_fundo[indice - 9]
                    else:
                        id_, produto, dose, preco, _stock = agenda[indice]
                    if item.get("id") != produto or raiz_p.get("reposicao", {}).get(slot) != id_:
                        erro(f"mercado_gago.nbt: produto/agenda divergentes em ({px},{py},{pz}), slot {slot}")
                    if raiz_p.get("precos", {}).get(slot) != preco or raiz_p.get("doses", {}).get(slot) != dose:
                        erro(f"mercado_gago.nbt: preço/dose divergem do Gago em ({px},{py},{pz}), slot {slot}")
    catalogo, _alimentos = catalogo_prateleira()
    esperados = {produto[1] for produto in catalogo}
    if not esperados.issubset(produtos_expostos):
        erro(f"mercado_gago.nbt: catálogo ausente nas gôndolas: {sorted(esperados - produtos_expostos)}")
    if not set(range((len(catalogo) + 8) // 9)).issubset(secoes_expostas):
        erro("mercado_gago.nbt: nem todas as seções de comida/bebida estão presentes")

    # v1.2.78 — A MOBÍLIA DE SERVIÇO preenche as laterais: engradado,
    # bebedouro, garrafão e lavadora são blocos do mod com modelos nativos.
    # bloco vanilla colado. Além do ID, a trava pega a ORIENTAÇÃO: bebedouro
    # e lavadora têm frente (bica/painel virados para dentro do salão), então
    # um "A" de frente para a parede é peça virada — o defeito mais chato,
    # porque parece certa de longe. As DUAS travas de corredor são o que
    # importa: a mobília pode existir na parede, mas NUNCA no corredor do
    # balcão nem nos laterais — é por eles que o freguês circula e que o Gago
    # alcança a gôndola.
    from gen_mercado import MOBILIA
    CORES_MOBILIA = {"E": "intoxicantes:engradado_mercado",
                     "I": "intoxicantes:bebedouro_mercado",
                     "i": "intoxicantes:bebedouro_mercado",
                     "F": "intoxicantes:bebedouro_garrafao",
                     "D": "intoxicantes:lavadora_mercado"}
    FRENTES = {"I": "east", "i": "west", "D": "west"}
    for parede, mz, pilha in MOBILIA:
        for altura, ch in pilha:
            if estado_em(parede, altura, mz) != CORES_MOBILIA[ch]:
                erro(f"mercado_gago.nbt: mobília ausente em ({parede},{altura},{mz}) — "
                     f"veio {estado_em(parede, altura, mz)!r}, esperado "
                     f"{CORES_MOBILIA[ch]} (a lista é a MOBILIA do gerador)")
            if ch in FRENTES:
                alvo = idx_por_pos.get((parede, altura, mz))
                st = alvo.get("state") if alvo else None
                frente = (paleta[st].get("properties", {}) or {}).get("facing") \
                    if isinstance(st, int) and st < len(paleta) else None
                if frente != FRENTES[ch]:
                    erro(f"mercado_gago.nbt: mobília virada em ({parede},{altura},{mz}) — "
                         f"facing={frente!r}, esperado {FRENTES[ch]!r} (a peça aponta "
                         f"para dentro do salão; de costas parece certa de longe)")
    for cz in range(9, 17):                  # do balcão até a porta
        for cx in (13, 14):
            if estado_em(cx, 1, cz) != "minecraft:air":
                erro(f"mercado_gago.nbt: o CORREDOR DO BALCÃO tem obstáculo em "
                     f"({cx},1,{cz}) (veio {estado_em(cx, 1, cz)!r}) — mobília "
                     f"na parede, nunca no caminho da porta pro caixa")
    for cx in (4, 5, 21, 22):   # corredores laterais (v1.2.79: encolheram)
        for cz in range(3, 17):
            if estado_em(cx, 1, cz) != "minecraft:air":
                erro(f"mercado_gago.nbt: o CORREDOR LATERAL tem obstáculo em "
                     f"({cx},1,{cz}) (veio {estado_em(cx, 1, cz)!r}) — o freguês "
                     f"precisa circular entre a parede e a ilha")            # Vãos laterais em X e corredor interno z7/z8 garantem acesso
            # a todas as colunas e aos dois lados da ilha.

    for cx in (7, 9, 17, 19):
        for cz in range(5, 11):
            for cy in (1, 2):
                if estado_em(cx, cy, cz) != 'minecraft:air':
                    erro(f'mercado_gago.nbt: vão lateral bloqueado em ({cx},{cy},{cz})')
    for cx in range(4, 23):
        for cz in (5, 7, 8, 10):
            for cy in (1, 2):
                if estado_em(cx, cy, cz) != 'minecraft:air':
                    erro(f'mercado_gago.nbt: corredor de compra bloqueado em ({cx},{cy},{cz})')
    # Todas as faces de venda: ar nos dois blocos de altura + chão firme.
    for px in COLUNAS_ILHA:
        for pz in FILEIRAS_ILHA:
            for corredor in (pz - 1, pz + 1):
                if estado_em(px, 0, corredor) == 'minecraft:air':
                    erro(f'mercado_gago.nbt: corredor sem chão em ({px},0,{corredor})')
                for cy in (1, 2):
                    if estado_em(px, cy, corredor) != 'minecraft:air':
                        erro(f'mercado_gago.nbt: face de venda inacessível ({px},{cy},{pz}), corredor z{corredor}')

    # Mini display "ABERTO · 24H": painel de LED ÚNICO com NBT no x18 (y1, z18);
    # o painel de OFERTAS fica no cabeça x8 (y2, z18) — os 2 coexistem.
    def painel_nbt_em(x, y, z):
        b = idx_por_pos.get((x, y, z))
        if b and id_da_entrada(b) == "intoxicantes:painel_led" and isinstance(b.get("nbt"), dict):
            return b["nbt"]
        return None

    paineis_nbt = [b for b in blocks if isinstance(b, dict)
                   and id_da_entrada(b) == "intoxicantes:painel_led" and "nbt" in b]
    if len(paineis_nbt) != 2:
        erro(f"mercado_gago.nbt: deve haver EXATAMENTE 2 paineis de LED com NBT "
             f"(ofertas x8 + mini display x18; tem {len(paineis_nbt)})")
    mini = painel_nbt_em(18, 1, 18)
    if mini is None:
        erro("mercado_gago.nbt: o mini display 'ABERTO 24H' sem NBT em (18,1,18) "
             "(lado direito da porta; o espelho do painel de ofertas)")
    elif mini.get("linha0") != "ABERTO":
        erro(f"mercado_gago.nbt: mini display sem 'ABERTO' na linha 0 (veio {mini.get('linha0')!r})")
    ofertas = painel_nbt_em(8, 2, 18)
    if ofertas is None:
        erro("mercado_gago.nbt: o painel de OFERTAS sem NBT no cabeça (8,2,18) "
             "— a linha nasce sem texto (bug do display duplicado)")

    # entidades: lista de compounds com blockPos/pos/nbt(id)
    ents = d.get("entities", [])
    if not isinstance(ents, list):
        erro("mercado_gago.nbt: 'entities' deveria ser TAG_List")
    for ent in ents:
        if not isinstance(ent, dict):
            erro("mercado_gago.nbt: entrada de entity nao e compound")
            continue
        if not isinstance(ent.get("blockPos"), list) or not isinstance(ent.get("pos"), list):
            erro("mercado_gago.nbt: entity sem blockPos/pos")
            continue
        inner = ent.get("nbt", {})
        eid = inner.get("id") if isinstance(inner, dict) else None
        if not isinstance(eid, str) or ":" not in eid:
            erro(f"mercado_gago.nbt: entity sem id valido: {eid!r}")
        elif eid.startswith("intoxicantes:"):
            ns, caminho = eid.split(":", 1)
            chave = f"{ns}/entities/{caminho}.json"
            if chave in MOD_JSONS or os.path.exists(os.path.join(DATA, chave)):
                pass  # definicao de entidade do mod existe
            # (a entidade tambem pode ser registrada por codigo; nao e erro)

    # o GAGO: EXATAMENTE 1, plantado no posto BALCAO_LOCAL (10,1,11) — fora
    # dali o gerenciador o teleportaria pra cima do balcão de outro jeito
    gagos = [ent for ent in ents if isinstance(ent, dict)
             and isinstance(ent.get("nbt"), dict)
             and ent["nbt"].get("id") == "intoxicantes:gago"]
    if len(gagos) != 1:
        erro(f"mercado_gago.nbt: EXATAMENTE 1 Gago no template (tem {len(gagos)})")
    elif gagos[0].get("blockPos") != [10, 1, 11]:
        erro(f"mercado_gago.nbt: Gago fora do posto BALCAO_LOCAL (10,1,11): "
             f"{gagos[0].get('blockPos')}")


# ============================================================ varredura
for rel, dado in sorted(MOD_JSONS.items()):
    caminho = rel[len("intoxicantes/"):] if rel.startswith("intoxicantes/") else rel
    if caminho.startswith("worldgen/template_pool/"):
        validar_template_pool(rel, dado)
    elif caminho.startswith("worldgen/structure_set/"):
        for s in dado.get("structures", []):
            ref = s.get("structure", "")
            if isinstance(ref, str) and ":" in ref and not ref_valida(ref, "worldgen/structure"):
                erro(f"{rel}: structure nao existe (vanilla nem mod): {ref}")
        pl = dado.get("placement", {})
        if pl.get("type") != "minecraft:random_spread":
            erro(f"{rel}: placement type estranho: {pl.get('type')}")
        elif pl.get("separation", 0) >= pl.get("spacing", 1):
            erro(f"{rel}: separation >= spacing (vanilla exige separation < spacing)")
    elif caminho.startswith("worldgen/structure/"):
        validar_structure(rel, dado)
    elif caminho.startswith("worldgen/placed_feature/"):
        validar_placed(rel, dado)
    elif caminho.startswith("worldgen/feature/"):
        validar_feature(rel, dado)
    elif caminho.startswith("recipe/"):
        validar_receita(rel, dado)

validar_nbt_mercado()

# ============================================================ resultado
if erros:
    print(f"\nvalidate_worldgen: {len(erros)} ERRO(S):")
    for e in erros:
        print("  X " + e)
    print("\nUm worldgen quebrado TRAVA a criacao de mundo (registro 'Unbound values').")
    print("Conserte antes de buildar.")
    sys.exit(1)
print(f"validate_worldgen: OK — {len(MOD_JSONS)} JSONs + NBT validados contra "
      f"{os.path.basename(JAR)} (DataVersion {DATA_VERSION})")
