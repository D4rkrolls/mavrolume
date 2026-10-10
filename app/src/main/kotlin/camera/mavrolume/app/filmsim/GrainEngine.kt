package camera.mavrolume.app.filmsim

/** Spatial silver-grain model shared by the live preview and full-resolution export. */
object GrainEngine {
    val glsl = """
float grainRandom(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33 + uSeed * 0.001);
    return fract((q.x + q.y) * q.z);
}
float grainLayer(vec2 p) {
    vec2 cell = floor(p);
    float coverage = 0.0;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec2 id = cell + vec2(float(x), float(y));
        vec2 center = id + vec2(grainRandom(id + 7.1), grainRandom(id + 19.7));
        vec2 delta = p - center;
        float radius = mix(0.19, 0.39, grainRandom(id + 31.3));
        float shape = exp(-dot(delta, delta) / (radius * radius));
        coverage += shape;
    }
    return coverage - 0.23;
}
float photographicGrain(vec2 uv, float grainSize, vec2 resolution) {
    float shortEdge = max(1.0, min(resolution.x, resolution.y));
    vec2 pixel = uv * resolution;
    float diameter = max(0.65, grainSize * shortEdge / 1080.0);
    float fine = grainLayer(pixel / diameter);
    float clustered = grainLayer(pixel / (diameter * 2.35) + 43.17);
    return fine * 0.78 + clustered * 0.22;
}
""".trimIndent()
}
