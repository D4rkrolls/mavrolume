from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[1]
body=(ROOT/'app/src/main/kotlin/camera/mavrolume/app/filmsim/MavrolumeShader.kt').read_text().split('"""')[1]
profiles=[]
for line in (ROOT/'app/src/main/kotlin/camera/mavrolume/app/filmsim/FilmSimulation.kt').read_text().splitlines():
 if not re.match(r'    [A-Z_]+\("',line): continue
 name=re.findall(r'"([^"]*)"',line)[0]
 v=[float(n) for n in re.findall(r'(-?[0-9.]+)f',line)]
 profiles.append(dict(legacy=not line.strip().startswith("STUDY_"),name=name,values=v[:11],toe=v[11],shoulder=v[12],green=v[13],blue=v[14],mr=v[15],mg=v[16]))
html='''<!doctype html><meta charset="utf-8"><title>Mavrolume GPU checks</title><style>body{background:#18181b;color:#eee;font:14px system-ui}canvas{width:512px;height:144px;image-rendering:pixelated}section{display:inline-block;width:520px}pre{white-space:pre-wrap}</style><h1>Mavrolume · shader checks</h1><pre id="result">Running…</pre><div id="previews"></div><script>'''
html+='const body='+json.dumps(body)+';const profiles='+json.dumps(profiles)+';'
previewSource=(ROOT/'app/src/main/kotlin/camera/mavrolume/app/camera/LutPreviewEffect.kt').read_text().split('private const val LUT_FRAGMENT_SHADER = """')[1].split('"""')[0]
previewSource=previewSource.replace('#version 310 es','#version 300 es').replace('#extension GL_OES_EGL_image_external_essl3 : require','').replace('samplerExternalOES','sampler2D').replace('vTexCoord','uv')
html+='const previewSource='+json.dumps(previewSource.strip())+';'

html+=r'''
const canvas=document.createElement('canvas');canvas.width=256;canvas.height=72;
const gl=canvas.getContext('webgl2',{preserveDrawingBuffer:true});let checks=0;
function assert(b,m){if(!b)throw Error(m);checks++;}
try {
assert(!!gl,'WebGL2 unavailable');
function shader(type,src){const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);assert(gl.getShaderParameter(s,gl.COMPILE_STATUS),gl.getShaderInfoLog(s));return s;}
const vs=shader(gl.VERTEX_SHADER,`#version 300 es
out vec2 uv;
void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=p;gl_Position=vec4(p*2.-1.,0,1);}`);
const fs=shader(gl.FRAGMENT_SHADER,`#version 300 es
precision highp float;
in vec2 uv;out vec4 color;uniform sampler2D uTexture;
#define SAMPLE(p) texture(uTexture,p)
`+body+`\nvoid main(){color=vec4(mavrolumeRender(uv),1.);}`.replace('\\n','\n'));
let program=gl.createProgram();gl.attachShader(program,vs);gl.attachShader(program,fs);gl.linkProgram(program);assert(gl.getProgramParameter(program,gl.LINK_STATUS),gl.getProgramInfoLog(program));gl.useProgram(program);
const input=new Uint8Array(256*72*4);
const swatches=[[195,139,105],[115,80,63],[234,197,171],[255,0,0],[0,255,0],[0,0,255],[235,230,215],[55,69,91]];
for(let y=0;y<72;y++)for(let x=0;x<256;x++){
 let c=y<24?[x,x,x]:y<48?swatches[Math.floor(x/32)]:[x,130,255-x];let i=(y*256+x)*4;input.set([...c,255],i);
}
const tex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,tex);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,256,72,0,gl.RGBA,gl.UNSIGNED_BYTE,input);
const names=['uProfileSat','uProfileContrast','uProfileWarmth','uProfileLift','uProfileTint','uShadowR','uShadowG','uShadowB','uHighlightR','uHighlightG','uHighlightB'];
function uniform(k,v){gl.uniform1f(gl.getUniformLocation(program,k),v);}
function render(p,strength=1){const controls={uLegacy:p.legacy?1:0,uStrength:strength,uSaturation:1,uTemperature:0,uTint:0,uGrain:0,uGrainSize:1.5,uSoftness:0,uAberration:0,uBloom:0,uHalation:0,uExposure:0,uBrightness:0,uContrast:1,uDynamicRange:0,uMids:0,uFade:0,uMute:0,uSeed:17,uToe:p.toe,uShoulder:p.shoulder,uGreenSat:p.green,uBlueSat:p.blue,uMonoR:p.mr,uMonoG:p.mg};Object.entries(controls).forEach(([k,v])=>uniform(k,v));names.forEach((k,i)=>uniform(k,p.values[i]));gl.uniform2f(gl.getUniformLocation(program,'uResolution'),256,72);gl.drawArrays(gl.TRIANGLES,0,3);const out=new Uint8Array(input.length);gl.readPixels(0,0,256,72,gl.RGBA,gl.UNSIGNED_BYTE,out);assert(gl.getError()===gl.NO_ERROR,'GL error');return out;}
let identity=render(profiles[0],0);assert(identity.every((v,i)=>Math.abs(v-input[i])<=1),'Strength zero not identity');
for(const p of profiles){let a=render(p);let prev=-1;
 for(let x=0;x<256;x++){let i=x*4,y=.2126*a[i]+.7152*a[i+1]+.0722*a[i+2];assert(y>=prev-1,'Non-monotonic '+p.name);prev=y;}
 if(!p.legacy) assert(a[1020]>248&&a[1021]>248&&a[1022]>248,'Tinted white '+p.name);
 if(!p.legacy) assert(a[0]<5&&a[1]<5&&a[2]<5,'Raised black '+p.name);
 if(p.values[0]===0)for(let i=0;i<a.length;i+=4)assert(Math.abs(a[i]-a[i+1])<=1&&Math.abs(a[i+1]-a[i+2])<=1,'Not monochrome '+p.name);
 if(p.legacy){
 const v=p.values,clamp=x=>Math.max(0,Math.min(1,x));
 for(let i=0;i<input.length;i+=4){
  const c=[input[i]/255,input[i+1]/255,input[i+2]/255],y=c[0]*.2126+c[1]*.7152+c[2]*.0722;
  for(let k=0;k<3;k++){
   let expected=(y+(c[k]-y)*v[0]-.5)*v[1]+.5;
   expected+=[v[2],v[2]*.12,-v[2]][k]*(.3+.7*c[k]);
   expected+=[v[4]*.5,-v[4],v[4]*.5][k]+v[5+k]*(1-y)*(1-y)+v[8+k]*y*y;
   expected=clamp(expected*(1-v[3])+v[3]);
   assert(Math.abs(a[i+k]-Math.round(expected*255))<=8,'Original filter mismatch '+p.name);
  }
 }
}
const section=document.createElement('section');section.innerHTML='<h3>'+p.name+'</h3>';const c=document.createElement('canvas');c.width=256;c.height=72;c.getContext('2d').drawImage(canvas,0,0);section.append(c);document.getElementById('previews').append(section);
}

// Compare the actual export tile mapping with the full-frame shader, including diffusion/grain.
const fullProgram=program;
const tileFs=shader(gl.FRAGMENT_SHADER,`#version 300 es
precision highp float;
in vec2 uv;out vec4 color;uniform sampler2D uTexture;
uniform vec2 uFullSize,uTileOrigin,uTileSize;
#define SAMPLE(p) texture(uTexture,((p)*uFullSize-uTileOrigin)/uTileSize)
`+body+`\nvoid main(){color=vec4(mavrolumeRender((uTileOrigin+uv*uTileSize)/uFullSize),1.);}`);
const tileProgram=gl.createProgram();gl.attachShader(tileProgram,vs);gl.attachShader(tileProgram,tileFs);gl.linkProgram(tileProgram);assert(gl.getProgramParameter(tileProgram,gl.LINK_STATUS),gl.getProgramInfoLog(tileProgram));
for(const p of [profiles[0],profiles[6],profiles[7],profiles[19]]){
 program=fullProgram;gl.useProgram(program);gl.viewport(0,0,256,72);
 gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,256,72,0,gl.RGBA,gl.UNSIGNED_BYTE,input);render(p);
 uniform('uGrain',.4);uniform('uBloom',.5);uniform('uHalation',.3);gl.drawArrays(gl.TRIANGLES,0,3);
 const reference=new Uint8Array(input.length);gl.readPixels(0,0,256,72,gl.RGBA,gl.UNSIGNED_BYTE,reference);
 for(let y=0;y<72;y+=36)for(let x=0;x<256;x+=128){
  const l=Math.max(0,x-3),t=Math.max(0,y-3),r=Math.min(256,x+131),b=Math.min(72,y+39),w=r-l,h=b-t;
  const pixels=new Uint8Array(w*h*4);for(let yy=0;yy<h;yy++)pixels.set(input.subarray(((t+yy)*256+l)*4,((t+yy)*256+r)*4),yy*w*4);
  program=tileProgram;gl.useProgram(program);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,pixels);
  // render supplies all shared profile/control uniforms; the final draw supplies tile coordinates.
  render(p);uniform('uGrain',.4);uniform('uBloom',.5);uniform('uHalation',.3);
  gl.uniform2f(gl.getUniformLocation(program,'uFullSize'),256,72);gl.uniform2f(gl.getUniformLocation(program,'uTileOrigin'),l,t);gl.uniform2f(gl.getUniformLocation(program,'uTileSize'),w,h);
  gl.viewport(0,0,w,h);gl.drawArrays(gl.TRIANGLES,0,3);const actual=new Uint8Array(w*h*4);gl.readPixels(0,0,w,h,gl.RGBA,gl.UNSIGNED_BYTE,actual);
  let maxError=0;for(let yy=0;yy<36;yy++)for(let xx=0;xx<128;xx++)for(let c=0;c<3;c++)maxError=Math.max(maxError,Math.abs(actual[((y-t+yy)*w+x-l+xx)*4+c]-reference[((y+yy)*256+x+xx)*4+c]));
  assert(maxError<=3,'Tile boundary mismatch '+p.name+': '+maxError);
 }
}

// Exercise the actual preview input shader with float samples beyond SDR bounds.
const inputFs=shader(gl.FRAGMENT_SHADER,previewSource),inputProgram=gl.createProgram();
gl.attachShader(inputProgram,vs);gl.attachShader(inputProgram,inputFs);gl.linkProgram(inputProgram);assert(gl.getProgramParameter(inputProgram,gl.LINK_STATUS),gl.getProgramInfoLog(inputProgram));gl.useProgram(inputProgram);
const samples=[[0,0,0],[1,1,1],[1.05,1.05,1.05],[1.5,1.5,1.5],[-.02,-.02,-.02],[1.08,.98,.9],[.9,1.08,.98],[.98,.9,1.08],[.7,.45,.3]];
const floating=new Float32Array(samples.flatMap(c=>[...c,1]));
gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA32F,samples.length,1,0,gl.RGBA,gl.FLOAT,floating);gl.uniform1i(gl.getUniformLocation(inputProgram,'uCamera'),0);gl.viewport(0,0,samples.length,1);gl.drawArrays(gl.TRIANGLES,0,3);
const normalized=new Uint8Array(samples.length*4);gl.readPixels(0,0,samples.length,1,gl.RGBA,gl.UNSIGNED_BYTE,normalized);assert(gl.getError()===gl.NO_ERROR,'Preview input GL failure');
samples.forEach((c,i)=>c.forEach((v,k)=>assert(Math.abs(normalized[i*4+k]-Math.round(Math.max(0,Math.min(1,v))*255))<=1,'Preview highlight/channel contamination at '+i)));

document.getElementById('result').textContent='PASS: '+checks+' GPU checks; 34 profiles; strength-zero identity, monotonic neutral ramps, modern white/black endpoints, neutral monochrome, profile math checks, preview overshoot normalization, full-frame/tile equivalence with grain and diffusion. Desktop WebGL2, not Android hardware validation.';
}catch(e){document.getElementById('result').textContent='FAIL: '+e.stack;}
</script>'''
# Ensure GLSL main starts on a new line rather than a literal backslash.
html=html.replace('`\\nvoid main()', '`\nvoid main()')
(ROOT/'tools/gpu-check.html').write_text(html)
