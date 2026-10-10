package camera.mavrolume.app.filmsim

/** Shared GLSL body for GLES preview and export. All recipes independently designed. */
object MavrolumeShader {
    val body = """
uniform float uLegacy;
uniform float uStrength, uSaturation, uTemperature, uTint;
uniform float uGrain, uGrainSize, uBloom, uHalation, uSoftness, uAberration;
uniform float uExposure, uBrightness, uContrast, uDynamicRange, uMids, uFade, uMute;
uniform float uProfileSat, uProfileContrast, uProfileWarmth, uProfileLift;
uniform float uProfileTint, uShadowR, uShadowG, uShadowB, uHighlightR, uHighlightG, uHighlightB;
uniform float uToe, uShoulder, uGreenSat, uBlueSat, uMonoR, uMonoG;
uniform vec2 uResolution;
uniform float uSeed;
float mavrolumeLuma(vec3 c) { return dot(c, vec3(0.2126,0.7152,0.0722)); }
// Luminance curve preserves endpoints and avoids independent channel clipping.
float mavrolumeCurve(float y, float contrast, float toe, float shoulder, float black) {
    float a = pow(max(y,0.0),contrast);
    float b = pow(max(1.0-y,0.0),contrast);
    float t = a / max(a+b,0.00001);
    t += toe*t*(1.0-t)*(1.0-t) - shoulder*t*t*(1.0-t);
    return black+(1.0-black)*t;
}
// Bring out-of-gamut chroma inward around luminance, retaining hue.
vec3 mavrolumeGamut(vec3 color) {
    float y = clamp(mavrolumeLuma(color),0.0,1.0);
    vec3 delta = color-y;
    float hi = max(delta.r,max(delta.g,delta.b));
    float lo = min(delta.r,min(delta.g,delta.b));
    float k = min(1.0,min((1.0-y)/max(hi,0.00001),y/max(-lo,0.00001)));
    return clamp(y+delta*k,0.0,1.0);
}
vec3 mavrolumeOriginalGrade(vec3 c) {
    c *= exp2(uExposure);
    c *= vec3(1.0 + uTemperature*0.16 + uTint*0.05, 1.0-uTint*0.10, 1.0-uTemperature*0.16+uTint*0.05);
    c = clamp(c, 0.0, 1.0);
    vec3 p = mix(vec3(mavrolumeLuma(c)), c, uProfileSat);
    p = (p-0.5)*uProfileContrast+0.5;
    p += vec3(uProfileWarmth, uProfileWarmth*0.12, -uProfileWarmth)* (0.3+0.7*c);
    float tone = mavrolumeLuma(c);
    p += vec3(uProfileTint*0.5,-uProfileTint,uProfileTint*0.5);
    p += vec3(uShadowR,uShadowG,uShadowB)*(1.0-tone)*(1.0-tone);
    p += vec3(uHighlightR,uHighlightG,uHighlightB)*tone*tone;
    p = p*(1.0-uProfileLift)+uProfileLift;
    c = mix(c, clamp(p,0.0,1.0),uStrength);
    c = mix(vec3(mavrolumeLuma(c)), c, uSaturation);
    c += uDynamicRange * (vec3(0.5)-c) * 0.45;
    c += uMids * 0.32 * c * (1.0-c)*4.0;
    c = (c-0.5)*uContrast+0.5+uBrightness;
    c = c*(1.0-uFade*0.32)+uFade*0.18;
    float vivid = max(c.r,max(c.g,c.b))-min(c.r,min(c.g,c.b));
    c = mix(c,vec3(mavrolumeLuma(c)),uMute*smoothstep(0.05,0.6,vivid)*0.8);
    return clamp(c,0.0,1.0);
}

vec3 mavrolumeGrade(vec3 c) {
    if(uLegacy > 0.5) return mavrolumeOriginalGrade(c);
    c = clamp(c*exp2(uExposure),0.0,1.0);
    c = mavrolumeGamut(c * vec3(1.0+uTemperature*0.16+uTint*0.05,1.0-uTint*0.10,1.0-uTemperature*0.16+uTint*0.05));
    float y = mavrolumeLuma(c);
    float chroma = max(c.r,max(c.g,c.b))-min(c.r,min(c.g,c.b));
    // A color-range mask, not face detection; protect warm midtones from oversaturation.
    float skin = smoothstep(0.0,0.08,c.r-c.g)*(1.0-smoothstep(0.12,0.32,c.r-c.g))
        *smoothstep(0.0,0.08,c.g-c.b)*(1.0-smoothstep(0.55,0.8,chroma));
    float green = max(0.0,c.g-max(c.r,c.b))/max(chroma,0.001);
    float blue = max(0.0,c.b-max(c.r,c.g))/max(chroma,0.001);
    float sat = uProfileSat*(1.0+uGreenSat*green+uBlueSat*blue);
    sat = mix(sat,1.0,skin*0.7);
    sat *= 1.0-0.10*smoothstep(0.72,1.0,y);
    float mono = dot(c,vec3(uMonoR,uMonoG,1.0-uMonoR-uMonoG));
    bool monochrome = uProfileSat < 0.001;
    float baseY = monochrome ? mono : y;
    float tone = mavrolumeCurve(baseY,uProfileContrast,uToe,uShoulder,uProfileLift);
    vec3 p = vec3(tone);
    if(!monochrome) {
        p += (c-y)*sat*mix(0.68,1.0,smoothstep(0.02,0.32,y));
        vec3 tint = vec3(uProfileWarmth+uProfileTint*0.5,-uProfileTint,-uProfileWarmth+uProfileTint*0.5);
        tint += mix(vec3(uShadowR,uShadowG,uShadowB),vec3(uHighlightR,uHighlightG,uHighlightB),smoothstep(0.2,0.8,y));
        tint -= mavrolumeLuma(tint);
        p += tint*(4.0*y*(1.0-y))*(1.0-0.7*skin)*1.35;
    }
    c = mix(c,mavrolumeGamut(p),uStrength);
    y = mavrolumeLuma(c);
    c = vec3(y)+(c-y)*uSaturation;
    float adjusted = mavrolumeCurve(y,uContrast,0.0,0.0,0.0);
    adjusted += uDynamicRange*(0.5-y)*y*(1.0-y)*1.8;
    adjusted += uMids*0.32*y*(1.0-y)*4.0;
    c += adjusted-y+uBrightness;
    c = c*(1.0-uFade*0.32)+uFade*0.18;
    float vivid = max(c.r,max(c.g,c.b))-min(c.r,min(c.g,c.b));
    c = mix(c,vec3(mavrolumeLuma(c)),uMute*smoothstep(0.05,0.6,vivid)*0.8);
    return mavrolumeGamut(c);
}
float mavrolumeNoise(vec2 cell) {
    vec3 p = fract(vec3(cell.xyx)*0.1031);
    p += dot(p,p.yzx+33.33+uSeed*0.001);
    return fract((p.x+p.y)*p.z)*2.0-1.0;
}
float mavrolumeSmoothNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f*f*(3.0-2.0*f);
    return mix(mix(mavrolumeNoise(i),mavrolumeNoise(i+vec2(1,0)),f.x),
               mix(mavrolumeNoise(i+vec2(0,1)),mavrolumeNoise(i+vec2(1,1)),f.x),f.y);
}
vec3 mavrolumeInput(vec2 uv) {
    vec3 source = SAMPLE(uv).rgb;
    if (uSoftness > 0.001) {
        vec2 px = 1.0 / uResolution;
        vec3 nearby = (SAMPLE(clamp(uv+vec2(px.x,0.0),0.0,1.0)).rgb
            + SAMPLE(clamp(uv-vec2(px.x,0.0),0.0,1.0)).rgb
            + SAMPLE(clamp(uv+vec2(0.0,px.y),0.0,1.0)).rgb
            + SAMPLE(clamp(uv-vec2(0.0,px.y),0.0,1.0)).rgb)*0.25;
        // Damp harsh digital edges without destroying the whole frame.
        source = mix(source,nearby,uSoftness*0.7);
    }
    if (uAberration > 0.001) {
        vec2 radial = uv-0.5;
        float distanceFromCenter = clamp(length(radial)*1.3,0.0,1.0);
        vec2 shift = radial * (uAberration*2.0*distanceFromCenter) / uResolution;
        source.r = SAMPLE(clamp(uv+shift,0.0,1.0)).r;
        source.b = SAMPLE(clamp(uv-shift,0.0,1.0)).b;
    }
    return source;
}
vec3 mavrolumeRender(vec2 uv) {
    vec3 c = mavrolumeGrade(mavrolumeInput(uv));
    float local = smoothstep(0.70,0.99,mavrolumeLuma(c));
    if (uBloom + uHalation > 0.001) {
        float nearLight = 0.0;
        float farLight = 0.0;
        float shortEdge = min(uResolution.x,uResolution.y);
        vec2 nearRadius = vec2(shortEdge*0.0028)/uResolution;
        vec2 farRadius = vec2(shortEdge*0.009)/uResolution;
        // Spread light based on source highlight intensity, not a uniform blur.
        for(int i=0;i<8;i++) {
            float angle = float(i)*0.78539816;
            vec2 direction = vec2(cos(angle),sin(angle));
            vec3 sampleColor = SAMPLE(clamp(uv+direction*nearRadius,0.0,1.0)).rgb;
            nearLight += smoothstep(0.64,0.98,mavrolumeLuma(sampleColor))/8.0;
            if(i < 4) {
                vec3 farColor = SAMPLE(clamp(uv+direction*farRadius,0.0,1.0)).rgb;
                farLight += smoothstep(0.78,1.0,mavrolumeLuma(farColor))/4.0;
            }
        }
        float halo = max(nearLight*0.65+farLight*0.35-local*0.46,0.0);
        c += uBloom*halo*vec3(0.34,0.32,0.29)*(1.0-c);
        c += uHalation*max(nearLight-local*0.62,0.0)*vec3(0.38,0.07,0.025)*(1.0-c);
    }
    float shortEdge = max(1.0,min(uResolution.x,uResolution.y));
    vec2 cell = uv*uResolution/(uGrainSize*shortEdge/1080.0);
    float lum = mavrolumeLuma(c);
    if (uGrain > 0.001) {
        // Fine irregular clumps plus larger low-amplitude variation; no square grain cells.
        float fine = mavrolumeNoise(floor(cell));
        float mid = mavrolumeSmoothNoise(cell*0.49+7.17);
        float coarse = mavrolumeSmoothNoise(cell*0.23+19.61);
        float texture = fine*0.62+mid*0.29+coarse*0.09;
        float sensitivity = mix(0.58,1.0,smoothstep(0.02,0.38,lum))
            * (1.0-0.58*smoothstep(0.78,1.0,lum));
        c += texture*uGrain*0.24*sensitivity;
    }
    // Saturated colours acquire slight density instead of turning neon.
    float chroma = max(c.r,max(c.g,c.b))-min(c.r,min(c.g,c.b));
    c *= 1.0-0.025*chroma*(1.0-lum)*uStrength;
    return clamp(c,0.0,1.0);
}
""".trimIndent()
}
