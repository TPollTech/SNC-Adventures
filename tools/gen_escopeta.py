"""Escopeta 12 pump-action longa e realista, usando apenas elements vanilla do Minecraft.

Visual inspirado em escopetas pump-action clássicas de calibre 12: coronha/fore-end de
nogueira, receiver e cano em aço oxidado/azulado, cano longo com vent rib, tubo de
magazine, porta de ejeção, loading port, action bars e guarda-mato vazado.
"""
import json, os, random
from PIL import Image, ImageDraw

ASSETS = os.path.join('src','main','resources','assets','intoxicantes')
TEX_SIZE = 128
TILE = 16

BASES = {
    'blued_steel': ((104,116,126),(22,28,34),(52,62,72)),
    'steel_dark':  ((70,76,82),(13,16,19),(34,39,44)),
    'steel_light': ((174,184,192),(82,92,102),(122,134,144)),
    'receiver':    ((92,98,103),(18,21,24),(48,53,58)),
    'bore':        ((28,30,32),(3,4,5),(11,13,15)),
    'brass':       ((222,194,118),(104,74,24),(165,132,62)),
    'rubber':      ((56,54,50),(13,12,11),(29,28,26)),
    'red':         ((225,58,46),(88,8,7),(165,28,21)),
    'walnut':      ((155,96,49),(52,28,13),(101,57,28)),
    'walnut_dark': ((101,57,29),(29,16,8),(62,34,17)),
    'walnut_light':((197,133,72),(89,51,25),(145,88,44)),
    'black':       ((58,60,61),(10,11,12),(27,29,31)),
}
POS={}
for i,name in enumerate(BASES): POS[name]=((i%8)*TILE,(i//8)*TILE)

rng=random.Random(870)
img=Image.new('RGBA',(TEX_SIZE,TEX_SIZE),(0,0,0,0))
d=ImageDraw.Draw(img)
for name,(px,py) in POS.items():
    light,dark,mid=BASES[name]
    for yy in range(TILE):
        t=yy/(TILE-1)
        # slightly nonlinear falloff: soft highlight in upper third
        t2=t**0.82
        base=tuple(int(light[c]*(1-t2)+dark[c]*t2) for c in range(3))
        for xx in range(TILE):
            noise=rng.randint(-5,5)
            if name.startswith('walnut'):
                # vertical/diagonal walnut grain
                wave=((xx*3+yy*2)%11)
                noise += 10 if wave in (0,1) else (-4 if wave in (5,6) else 0)
            elif name in ('blued_steel','steel_dark','steel_light','receiver'):
                noise += 4 if ((xx*7+yy*3)%19==0) else 0
                if (xx+yy*2)%29==0: noise-=7
            elif name=='brass':
                noise += 5 if (xx+yy)%9==0 else 0
            col=tuple(max(0,min(255,v+noise)) for v in base)
            d.point((px+xx,py+yy),fill=(*col,255))
    d.line((px,py,px+TILE-1,py),fill=(*light,255))
    d.line((px,py+TILE-1,px+TILE-1,py+TILE-1),fill=(*dark,255))

# black rubber gets very subtle horizontal ribs
px,py=POS['rubber']
for yy in (4,8,12): d.line((px,py+yy,px+TILE-1,py+yy),fill=(22,21,20,255))
# front bead/safety red: dark surround + red highlight
px,py=POS['red']; d.rectangle([px,py,px+TILE-1,py+TILE-1],fill=(20,20,21,255)); d.ellipse([px+4,py+4,px+11,py+11],fill=(210,42,32,255)); d.point((px+6,py+5),fill=(255,142,102,255))

tex_path=os.path.join(ASSETS,'textures','item','escopeta.png')
os.makedirs(os.path.dirname(tex_path),exist_ok=True); img.save(tex_path)

def uv(name):
    px,py=POS[name]; scale=TEX_SIZE/16.0
    return [px/scale,py/scale,(px+TILE)/scale,(py+TILE)/scale]

AXIS_REMAP={'x':'y','y':'z','z':'x'}

def elemento(nome,de,ate,lados,rot=None):
    def remap(p):
        x,y,z=p; return [z,x,y]
    faces={lado:{'uv':uv(mat),'texture':'#escopeta'} for lado,mat in lados.items()}
    el={'name':nome,'from':remap(de),'to':remap(ate),'faces':faces}
    if rot:
        el['rotation']={'origin':remap(rot['origin']),'axis':AXIS_REMAP.get(rot['axis'],rot['axis']),'angle':rot['angle'],'rescale':True}
    return el

def mats(main='receiver',top=None,bottom=None,side=None,ends=None):
    top=top or main; bottom=bottom or main; side=side or main; ends=ends or side
    return {'up':top,'down':bottom,'north':side,'south':side,'east':ends,'west':ends}

E=[]
# ------------------------------ stock: longer, slimmer, classic field/police pump proportions
ROT_STOCK={'origin':[6.35,8.15,8.0],'axis':'z','angle':0}
E.append(elemento('stock_neck',[5.10,6.38,6.82],[7.15,9.62,9.18],mats('walnut','walnut_light','walnut_dark','walnut','walnut_dark')))
# revised stock: less dropped / less "deitada", with a more natural field-stock line
E.append(elemento('stock_mid',[0.55,5.55,6.66],[5.65,8.95,9.34],mats('walnut','walnut_light','walnut_dark','walnut','walnut_dark'),ROT_STOCK))
E.append(elemento('stock_butt',[-3.25,4.75,6.56],[1.00,8.35,9.44],mats('walnut','walnut_light','walnut_dark','walnut','walnut_dark'),ROT_STOCK))
E.append(elemento('butt_pad',[-3.92,4.60,6.48],[-3.22,8.50,9.52],mats('rubber'),ROT_STOCK))
E.append(elemento('cheek_piece',[0.25,8.18,6.82],[4.90,8.72,9.18],mats('walnut_light','walnut_light','walnut','walnut_light','walnut_dark'),ROT_STOCK))
# rounded-looking transition/pistol grip via two stepped boxes
E.append(elemento('grip_upper',[5.45,5.60,6.92],[7.05,7.42,9.08],mats('walnut','walnut_light','walnut_dark','walnut','walnut_dark'),ROT_STOCK))
E.append(elemento('grip_lower',[4.72,4.48,7.00],[6.20,6.45,9.00],mats('walnut_dark','walnut','walnut_dark','walnut_dark','walnut_dark'),ROT_STOCK))
E.append(elemento('grip_cap',[4.58,4.35,7.18],[5.55,4.65,8.82],mats('rubber'),ROT_STOCK))

# ------------------------------ receiver, kept compact like a real pump-action receiver
E.append(elemento('receiver_main',[6.85,6.52,6.30],[10.30,10.45,9.70],mats('receiver','steel_light','steel_dark','receiver','steel_dark')))
E.append(elemento('receiver_top',[7.10,10.45,6.58],[10.12,10.83,9.42],mats('steel_light','steel_light','receiver','receiver','receiver')))
E.append(elemento('receiver_lower',[7.15,6.02,6.58],[9.72,6.55,9.42],mats('steel_dark')))
E.append(elemento('ejection_port',[7.70,8.74,9.70],[9.72,10.00,10.03],mats('bore')))
E.append(elemento('ejection_lip',[7.58,9.95,9.73],[9.86,10.17,10.07],mats('steel_light')))
E.append(elemento('loading_port',[7.48,5.70,7.05],[9.58,6.06,8.95],mats('bore')))
E.append(elemento('pin_rear',[7.38,7.38,9.72],[7.62,7.66,10.04],mats('steel_light')))
E.append(elemento('pin_front',[8.92,7.38,9.72],[9.16,7.66,10.04],mats('steel_light')))
E.append(elemento('safety',[6.94,9.72,9.72],[7.22,10.00,10.06],mats('red')))

# ------------------------------ long 12-ga barrel; much longer and slimmer than previous model
BARREL_END=25.20
E.append(elemento('barrel_core',[10.05,8.52,7.34],[BARREL_END,9.45,8.66],mats('blued_steel','steel_light','steel_dark','blued_steel','bore')))
E.append(elemento('barrel_top',[10.18,9.43,7.52],[25.10,9.70,8.48],mats('steel_light','steel_light','blued_steel','blued_steel','bore')))
E.append(elemento('barrel_bottom',[10.18,8.27,7.52],[25.10,8.54,8.48],mats('steel_dark','steel_dark','steel_dark','blued_steel','bore')))
# ventilated rib like a long field shotgun
E.append(elemento('vent_rib',[10.52,9.70,7.76],[24.93,9.93,8.24],mats('steel_dark')))
for i,x in enumerate([11.10,13.45,15.80,18.15,20.50,22.85,24.45]):
    E.append(elemento(f'rib_bridge_{i}',[x,9.88,7.64],[x+0.13,10.10,8.36],mats('steel_dark')))
# muzzle and actual dark bore, no decorative brass collar
E.append(elemento('muzzle_collar',[24.93,8.22,7.12],[25.48,9.75,8.88],mats('steel_dark','blued_steel','steel_dark','steel_dark','bore')))
E.append(elemento('muzzle_bore',[25.46,8.48,7.38],[25.88,9.50,8.62],mats('bore')))
# tiny brass bead at the front sight, typical classic shotgun detail
E.append(elemento('front_bead',[24.83,9.94,7.88],[25.02,10.18,8.12],mats('brass','brass','steel_dark','brass','steel_dark')))

# ------------------------------ magazine tube under barrel
E.append(elemento('mag_tube',[10.00,7.04,7.55],[23.30,8.00,8.45],mats('steel_dark','blued_steel','bore','steel_dark','bore')))
E.append(elemento('mag_cap',[22.95,6.98,7.43],[23.50,8.07,8.57],mats('steel_dark','steel_light','steel_dark','steel_dark','steel_dark')))
E.append(elemento('barrel_clamp',[21.55,7.82,7.04],[21.92,9.52,8.96],mats('receiver','steel_light','steel_dark','receiver','steel_dark')))

# ------------------------------ wooden pump/fore-end: moved forward and made longer like a real 12-ga pump
E.append(elemento('forend_core',[13.70,6.38,6.42],[19.10,9.12,9.58],mats('walnut','walnut_light','walnut_dark','walnut','walnut_dark')))
for i,x in enumerate([13.92,14.42,14.92,15.42,15.92,16.42,16.92,17.42,17.92,18.42,18.92]):
    E.append(elemento(f'forend_rib_{i}',[x,6.24,6.30],[x+0.13,9.22,9.70],mats('walnut_dark')))
# steel action bars back into receiver, reaching the more forward pump
E.append(elemento('action_bar_right',[9.28,6.68,9.57],[14.15,7.00,9.80],mats('steel_light')))
E.append(elemento('action_bar_left',[9.28,6.68,6.20],[14.15,7.00,6.43],mats('steel_light')))

# ------------------------------ trigger group
E.append(elemento('guard_bottom',[6.38,4.98,7.48],[8.62,5.31,8.52],mats('black')))
E.append(elemento('guard_rear',[6.20,5.18,7.48],[6.55,6.46,8.52],mats('black')))
E.append(elemento('guard_front',[8.48,5.18,7.48],[8.83,6.46,8.52],mats('black')))
ROT_TRIGGER={'origin':[7.55,6.14,8.0],'axis':'z','angle':-22.5}
E.append(elemento('trigger',[7.38,5.53,7.74],[7.70,6.60,8.26],mats('steel_light','receiver','steel_dark','receiver','steel_dark'),ROT_TRIGGER))
E.append(elemento('rear_sight',[9.34,10.80,7.76],[9.62,11.10,8.24],mats('steel_dark','steel_light','steel_dark','steel_dark','steel_dark')))
E.append(elemento('sling_stud',[-0.15,3.54,7.76],[0.18,3.94,8.24],mats('steel_dark'),ROT_STOCK))

modelo={
    'gui_light':'side',
    'textures':{'escopeta':'intoxicantes:item/escopeta','particle':'intoxicantes:item/escopeta'},
    'elements':E,
    'display':{
        # Larger in third person: roughly a full-length long gun relative to a Minecraft player.
        'thirdperson_righthand':{'rotation':[-90,0,-60],'translation':[2.1,-0.4,-4.2],'scale':[1.08,1.08,1.08]},
        'thirdperson_lefthand':{'rotation':[-90,0,30],'translation':[2.1,-0.4,-4.2],'scale':[1.08,1.08,1.08]},
        'firstperson_righthand':{'rotation':[-90,-2,-8],'translation':[1.20,1.85,0.20],'scale':[0.94,0.94,0.94]},
        'firstperson_lefthand':{'rotation':[-90,2,8],'translation':[1.20,1.85,0.20],'scale':[0.94,0.94,0.94]},
        # Smaller GUI/fixed transforms because model bounds are intentionally much longer than 0..16.
        'gui':{'rotation':[10,45,30],'translation':[0,0,0],'scale':[0.48,0.48,0.48]},
        'fixed':{'rotation':[0,0,0],'translation':[0,1,0],'scale':[0.34,0.34,0.34]},
        'ground':{'rotation':[0,0,0],'translation':[0,1,0],'scale':[0.27,0.27,0.27]},
    }
}
model_path=os.path.join(ASSETS,'models','item','escopeta.json'); os.makedirs(os.path.dirname(model_path),exist_ok=True)
with open(model_path,'w',encoding='utf-8') as f: json.dump(modelo,f,indent=2,ensure_ascii=False); f.write('\n')
item_path=os.path.join(ASSETS,'items','escopeta.json'); os.makedirs(os.path.dirname(item_path),exist_ok=True)
with open(item_path,'w',encoding='utf-8') as f: json.dump({'model':{'type':'minecraft:model','model':'intoxicantes:item/escopeta'}},f,indent=2); f.write('\n')
print(f'Escopeta 12 REALISTA/LONGA REV2: {len(E)} elements | textura {TEX_SIZE}x{TEX_SIZE} | bounds longitudinais ~29.6 unidades')
