package com.villageforge.graphics

import android.view.Choreographer
import android.view.Surface
import com.google.android.filament.*
import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialPackage
import com.villageforge.config.Theme
import com.villageforge.config.Town
import com.villageforge.config.WorldLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.flow.MutableStateFlow

object Transforms {
    fun trs(tx: Float, ty: Float, tz: Float, sx: Float, sy: Float, sz: Float, yawDegrees: Float = 0f): FloatArray {
        val out = FloatArray(16)
        trsInto(out, tx, ty, tz, sx, sy, sz, yawDegrees)
        return out
    }
    fun trsInto(out: FloatArray, tx: Float, ty: Float, tz: Float, sx: Float, sy: Float, sz: Float, yawDegrees: Float) {
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val c = cos(yaw).toFloat(); val s = sin(yaw).toFloat()
        out[0]=c*sx; out[1]=0f; out[2]=-s*sx; out[3]=0f
        out[4]=0f; out[5]=sy; out[6]=0f; out[7]=0f
        out[8]=s*sz; out[9]=0f; out[10]=c*sz; out[11]=0f
        out[12]=tx; out[13]=ty; out[14]=tz; out[15]=1f
    }
    fun rootInto(out: FloatArray, tx: Float, ty: Float, tz: Float, yawRadians: Float, pitchRadians: Float = 0f) {
        val c = cos(yawRadians); val s = sin(yawRadians)
        val cp = cos(pitchRadians); val sp = sin(pitchRadians)
        // rotY(yaw) * rotX(pitch) — pitch leans the whole rig forward/back.
        out[0]=c;    out[1]=0f; out[2]=-s;   out[3]=0f
        out[4]=s*sp; out[5]=cp; out[6]=c*sp; out[7]=0f
        out[8]=s*cp; out[9]=-sp; out[10]=c*cp; out[11]=0f
        out[12]=tx; out[13]=ty; out[14]=tz; out[15]=1f
    }
    fun translation(out: FloatArray, tx: Float, ty: Float, tz: Float) {
        out[0]=1f;out[1]=0f;out[2]=0f;out[3]=0f;out[4]=0f;out[5]=1f;out[6]=0f;out[7]=0f
        out[8]=0f;out[9]=0f;out[10]=1f;out[11]=0f;out[12]=tx;out[13]=ty;out[14]=tz;out[15]=1f
    }
    fun rotationX(out: FloatArray, radians: Float) {
        val c = cos(radians); val s = sin(radians)
        out[0]=1f;out[1]=0f;out[2]=0f;out[3]=0f;out[4]=0f;out[5]=c;out[6]=s;out[7]=0f
        out[8]=0f;out[9]=-s;out[10]=c;out[11]=0f;out[12]=0f;out[13]=0f;out[14]=0f;out[15]=1f
    }
    fun scale(out: FloatArray, sx: Float, sy: Float, sz: Float) {
        out[0]=sx;out[1]=0f;out[2]=0f;out[3]=0f;out[4]=0f;out[5]=sy;out[6]=0f;out[7]=0f
        out[8]=0f;out[9]=0f;out[10]=sz;out[11]=0f;out[12]=0f;out[13]=0f;out[14]=0f;out[15]=1f
    }
    fun multiply(out: FloatArray, a: FloatArray, b: FloatArray) {
        for (c in 0..3) {
            val b0=b[c*4];val b1=b[c*4+1];val b2=b[c*4+2];val b3=b[c*4+3]
            out[c*4]=a[0]*b0+a[4]*b1+a[8]*b2+a[12]*b3
            out[c*4+1]=a[1]*b0+a[5]*b1+a[9]*b2+a[13]*b3
            out[c*4+2]=a[2]*b0+a[6]*b1+a[10]*b2+a[14]*b3
            out[c*4+3]=a[3]*b0+a[7]*b1+a[11]*b2+a[15]*b3
        }
    }
}

class AssetFactory(private val engine: Engine) {
    class Mesh(val vertexBuffer: VertexBuffer, val indexBuffer: IndexBuffer, val indexCount: Int, val bounds: Box)

    private val entities = ArrayList<Int>()
    private val materialInstances = ArrayList<MaterialInstance>()
    private val meshes = ArrayList<Mesh>()
    private var litMaterial: Material? = null
    private var smokeMaterial: Material? = null
    private var teardownDone = false

    init { MaterialBuilder.init() }

    private fun litMaterialDef(): Material {
        if (litMaterial == null) {
            // NOTE: .material(...) is REQUIRED. Without a fragment shader body,
            // Filament compiles a valid material whose `material.baseColor`
            // stays at its default (1,1,1) — every object renders pure WHITE
            // no matter what baseColor uniform values we set at runtime.
            val pkg = MaterialBuilder()
                .name("vf_lit")
                .platform(MaterialBuilder.Platform.MOBILE)
                .shading(MaterialBuilder.Shading.LIT)
                .materialDomain(MaterialBuilder.MaterialDomain.SURFACE)
                .blending(MaterialBuilder.BlendingMode.OPAQUE)
                .culling(MaterialBuilder.CullingMode.BACK)
                .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "baseColor")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughness")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "metallic")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "emissiveColor")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "emissiveStrength")
                .material(
                    "void material(inout MaterialInputs material) {\n" +
                        "    prepareMaterial(material);\n" +
                        "    material.baseColor = float4(materialParams.baseColor, 1.0);\n" +
                        "    material.roughness = materialParams.roughness;\n" +
                        "    material.metallic = materialParams.metallic;\n" +
                        "    material.emissive = float4(materialParams.emissiveColor * materialParams.emissiveStrength, 1.0);\n" +
                        "}\n"
                )
                .build(engine)
            check(pkg.isValid) { "vf_lit material package failed to compile" }
            litMaterial = Material.Builder()
                .payload(pkg.buffer, pkg.buffer.remaining())
                .build(engine)
        }
        return litMaterial!!
    }

    private fun smokeMaterialDef(): Material {
        if (smokeMaterial == null) {
            val pkg = MaterialBuilder()
                .name("vf_smoke")
                .platform(MaterialBuilder.Platform.MOBILE)
                .shading(MaterialBuilder.Shading.UNLIT)
                .materialDomain(MaterialBuilder.MaterialDomain.SURFACE)
                .blending(MaterialBuilder.BlendingMode.TRANSPARENT)
                .culling(MaterialBuilder.CullingMode.BACK)
                .depthWrite(false)
                .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "baseColor")
                .material(
                    "void material(inout MaterialInputs material) {\n" +
                        "    prepareMaterial(material);\n" +
                        "    material.baseColor = materialParams.baseColor;\n" +
                        "}\n"
                )
                .build(engine)
            check(pkg.isValid) { "vf_smoke material package failed to compile" }
            smokeMaterial = Material.Builder()
                .payload(pkg.buffer, pkg.buffer.remaining())
                .build(engine)
        }
        return smokeMaterial!!
    }

    fun material(
        color: Theme.Rgb,
        roughness: Float,
        metallic: Float = Theme.METALLIC_DEFAULT,
        emissive: Theme.Rgb? = null,
        emissiveStrength: Float = 0f,
    ): MaterialInstance {
        val instance = litMaterialDef().createInstance()
        instance.setParameter("baseColor", color.r, color.g, color.b)
        instance.setParameter("roughness", roughness)
        instance.setParameter("metallic", metallic)
        val e = emissive ?: color
        instance.setParameter("emissiveColor", e.r, e.g, e.b)
        instance.setParameter("emissiveStrength", emissiveStrength)
        materialInstances.add(instance)
        return instance
    }

    /** Unlit translucent material for furnace smoke puffs (alpha animated per instance). */
    fun smokeInstance(): MaterialInstance {
        val instance = smokeMaterialDef().createInstance()
        instance.setParameter("baseColor", Theme.SMOKE.r, Theme.SMOKE.g, Theme.SMOKE.b, 0.5f)
        materialInstances.add(instance)
        return instance
    }

    fun addRenderable(scene: Scene, mesh: Mesh, instance: MaterialInstance, transform: FloatArray): Int {
        return addRenderable(scene, mesh, instance, transform, castShadows = true)
    }

    /** v2.2 — flat overlays (rain, window glow) skip the shadow pass. */
    fun addRenderable(
        scene: Scene, mesh: Mesh, instance: MaterialInstance, transform: FloatArray,
        castShadows: Boolean,
    ): Int {
        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(mesh.bounds)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, mesh.vertexBuffer, mesh.indexBuffer, 0, mesh.indexCount)
            .material(0, instance)
            .castShadows(castShadows).receiveShadows(castShadows)
            .build(engine, entity)
        val transformManager = engine.transformManager
        transformManager.create(entity)
        transformManager.setTransform(transformManager.getInstance(entity), transform)
        scene.addEntity(entity)
        entities.add(entity)
        return entity
    }

    val box: Mesh by lazy { buildBox() }
    val rockVariants: List<Mesh> by lazy { (0 until 6).map { buildRock(it) } }
    val terrainParts: List<Mesh> by lazy { buildTerrain() }

    // ---- v2.3 curved primitive library -------------------------------------
    // The whole world was boxes, which read as "boxy". These kill that look
    // while staying inside the same cheap fully-dynamic no-asset budget:
    // every primitive is one small shared mesh, reused by hundreds of
    // renderables through per-instance transforms like `box` always was.

    /** Chamfered box, same unit footprint as `box` (y 0..1). */
    val roundedBox: Mesh by lazy { buildRoundedBox() }
    /** 6-sided column, y 0..1, diameter 1. */
    val cyl6: Mesh by lazy { prism(6, 1f) }
    /** 8-sided column, y 0..1, diameter 1. */
    val cyl8: Mesh by lazy { prism(8, 1f) }
    /** 8-sided taper (top diameter 0.55): chimneys, towers, monolith. */
    val taper8: Mesh by lazy { prism(8, 0.55f) }
    /** 6-sided cone: pine layers, spires. */
    val cone6: Mesh by lazy { prism(6, 0.06f) }
    /** 4-sided cone: wheat spikes, scatter blades. */
    val cone4: Mesh by lazy { prism(4, 0.05f) }
    /** Hemisphere, y 0..0.5, radius 0.5, flat side down. */
    val dome: Mesh by lazy { buildDome() }
    /** Elongated octahedron, y 0..1, widest at y 0.4: flames, shards, windows. */
    val gem: Mesh by lazy { buildGem() }
    /** Rounded-top flat panel facing +z: the furnace mouth arch. */
    val archPanel: Mesh by lazy { buildArchPanel() }
    /** Windmill cloth sail: tapered slab, y 0.2..2.5, root half-width 0.24. */
    val sail: Mesh by lazy { buildSail() }
    /** Three organic faceted canopy blobs, radius ~0.55, centred on origin. */
    val canopyBlobs: List<Mesh> by lazy { (0 until 3).map { buildCanopyBlob(it) } }
    /** Four tapered, jittered cliff slabs, y 0..1 like `box`. */
    val cliffVariants: List<Mesh> by lazy { (0 until 4).map { buildCliffSlab(it) } }
    /** One merged mesh of grass tufts scattered over the walkable ground. */
    val tuftScatter: Mesh by lazy { buildScatter(pebbles = false) }
    /** One merged mesh of pebble chips scattered over the walkable ground. */
    val pebbleScatter: Mesh by lazy { buildScatter(pebbles = true) }

    /** Cached n-sided prism with a custom top scale (both ends capped). */
    fun prism(sides: Int, topScale: Float): Mesh =
        prismCache.getOrPut(sides to topScale) { buildPrism(sides, topScale) }
    private val prismCache = HashMap<Pair<Int, Float>, Mesh>()

    private val roofCache = ArrayList<Pair<FloatArray, Mesh>>()

    /**
     * Solid gable roof: a pentagon profile (eave / slope / ridge cap / slope /
     * eave, closed underneath) extruded along X. Eaves sit at y 0 and the
     * overhang hangs past both walls, so ONE renderable replaces the old
     * two-tilted-boxes-plus-ridge assembly.
     */
    fun gableRoof(w: Float, d: Float, pitch: Float, overhang: Float): Mesh {
        val key = floatArrayOf(w, d, pitch, overhang)
        for ((k, m) in roofCache) if (k.contentEquals(key)) return m
        val mesh = buildGableRoof(w, d, pitch, overhang)
        roofCache.add(key to mesh)
        return mesh
    }

    private fun buildBox(): Mesh {
        val h = 0.5f
        val faces = arrayOf(
            floatArrayOf(0f,0f,1f) to floatArrayOf(-h,0f,h, h,0f,h, h,1f,h, -h,1f,h),
            floatArrayOf(1f,0f,0f) to floatArrayOf(h,0f,h, h,0f,-h, h,1f,-h, h,1f,h),
            floatArrayOf(0f,0f,-1f) to floatArrayOf(h,0f,-h, -h,0f,-h, -h,1f,-h, h,1f,-h),
            floatArrayOf(-1f,0f,0f) to floatArrayOf(-h,0f,-h, -h,0f,h, -h,1f,h, -h,1f,-h),
            floatArrayOf(0f,1f,0f) to floatArrayOf(-h,1f,h, h,1f,h, h,1f,-h, -h,1f,-h),
        )
        val positions = ArrayList<Float>(60); val normals = ArrayList<Float>(60); val indices = ArrayList<Int>(30)
        for ((normal, quad) in faces) {
            val base = positions.size / 3
            for (i in 0 until 4) {
                for (k in 0 until 3) { positions.add(quad[i*3+k]); normals.add(normal[k]) }
            }
            indices.add(base); indices.add(base+1); indices.add(base+2)
            indices.add(base); indices.add(base+2); indices.add(base+3)
        }
        return registerMesh(newVertexBuffer(positions.toFloatArray(), normals.toFloatArray()), newIndexBuffer(indices), indices.size, Box(floatArrayOf(0f,0.5f,0f), floatArrayOf(h,0.5f,h)))
    }

    private fun buildRock(seed: Int): Mesh {
        val n = 4
        val positions = ArrayList<Float>(384); val normals = ArrayList<Float>(384); val indices = ArrayList<Int>(256)
        val faces = arrayOf(
            arrayOf(floatArrayOf(-1f,1f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(1f,1f,-1f), floatArrayOf(-1f,1f,-1f)),
            arrayOf(floatArrayOf(-1f,-1f,1f), floatArrayOf(1f,-1f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(-1f,1f,1f)),
            arrayOf(floatArrayOf(1f,-1f,-1f), floatArrayOf(-1f,-1f,-1f), floatArrayOf(-1f,1f,-1f), floatArrayOf(1f,1f,-1f)),
            arrayOf(floatArrayOf(1f,-1f,1f), floatArrayOf(1f,-1f,-1f), floatArrayOf(1f,1f,-1f), floatArrayOf(1f,1f,1f)),
            arrayOf(floatArrayOf(-1f,-1f,-1f), floatArrayOf(-1f,-1f,1f), floatArrayOf(-1f,1f,1f), floatArrayOf(-1f,1f,-1f)),
        )
        for (corners in faces) {
            val pts = Array(n) { i -> Array(n) { j ->
                val u = i.toFloat()/(n-1); val v = j.toFloat()/(n-1)
                val p = FloatArray(3)
                for (k in 0..2) p[k] = corners[0][k]*(1-u)*(1-v)+corners[1][k]*u*(1-v)+corners[2][k]*u*v+corners[3][k]*(1-u)*v
                rockPoint(p, seed)
            }}
            for (i in 0 until n-1) for (j in 0 until n-1) {
                val a=pts[i][j]; val b=pts[i+1][j]; val c=pts[i+1][j+1]; val d=pts[i][j+1]
                val nx=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1])
                val ny=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2])
                val nz=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
                val len = sqrt(nx*nx+ny*ny+nz*nz)
                val base = positions.size/3
                for (p in arrayOf(a,b,c,d)) {
                    positions.add(p[0]); positions.add(p[1]); positions.add(p[2])
                    normals.add(nx/len); normals.add(ny/len); normals.add(nz/len)
                }
                indices.add(base); indices.add(base+1); indices.add(base+2)
                indices.add(base); indices.add(base+2); indices.add(base+3)
            }
        }
        return registerMesh(newVertexBuffer(positions.toFloatArray(), normals.toFloatArray()), newIndexBuffer(indices), indices.size, Box(floatArrayOf(0f,0.75f,0f), floatArrayOf(1.4f,1.0f,1.4f)))
    }

    private fun rockPoint(p: FloatArray, seed: Int): FloatArray {
        val ix = Math.round(p[0]*2f); val iy = Math.round(p[1]*2f); val iz = Math.round(p[2]*2f)
        val jx = (hash3(ix,iy,iz,seed*31+1)-0.5f)*0.6f
        val jy = (hash3(ix,iy,iz,seed*31+2)-0.5f)*0.4f
        val jz = (hash3(ix,iy,iz,seed*31+3)-0.5f)*0.6f
        return floatArrayOf(p[0]+jx, p[1]*0.65f+0.75f+jy, p[2]+jz)
    }

    private fun buildTerrain(): List<Mesh> {
        val cell = WorldLayout.TERRAIN_CELL
        val x0 = WorldLayout.HOLLOW_X_MIN   // west edge of the Crystal Hollow
        val x1 = WorldLayout.VALLEY_WIDTH / 2f
        val z0 = WorldLayout.CANYON_Z_MIN - 1.5f
        val z1 = WorldLayout.VALLEY_Z_MAX
        val nx = ((x1 - x0) / cell).toInt() + 1
        val nz = ((z1 - z0) / cell).toInt() + 1
        val heights = FloatArray(nx * nz); val positions = FloatArray(nx * nz * 3)
        for (j in 0 until nz) for (i in 0 until nx) {
            val x = x0 + i * cell; val z = z0 + j * cell; val h = WorldLayout.groundHeight(x, z); val v = j * nx + i
            heights[v] = h; positions[v * 3] = x; positions[v * 3 + 1] = h; positions[v * 3 + 2] = z
        }
        val normals = FloatArray(nx * nz * 3)
        fun heightAt(i: Int, j: Int): Float = heights[j.coerceIn(0, nz - 1) * nx + i.coerceIn(0, nx - 1)]
        for (j in 0 until nz) for (i in 0 until nx) {
            val v = j * nx + i
            val dx = (heightAt(i + 1, j) - heightAt(i - 1, j)) / (2f * cell)
            val dz = (heightAt(i, j + 1) - heightAt(i, j - 1)) / (2f * cell)
            val inv = 1f / sqrt(dx * dx + 1f + dz * dz)
            normals[v * 3] = -dx * inv; normals[v * 3 + 1] = inv; normals[v * 3 + 2] = -dz * inv
        }
        val valleyCount = Theme.GRASS.size
        val canyonCount = Theme.CANYON_GRASS.size
        val variantIndices = Array(valleyCount + canyonCount) { ArrayList<Int>() }
        for (j in 0 until nz - 1) for (i in 0 until nx - 1) {
            val v00 = j * nx + i; val v01 = (j + 1) * nx + i; val v11 = v01 + 1; val v10 = v00 + 1
            val cx = x0 + (i + 0.5f) * cell
            val cz = z0 + (j + 0.5f) * cell
            if (WorldLayout.inCanyonZone(cx, cz)) {
                val variant = (WorldLayout.hash01(i, j, 8.9f) * canyonCount).toInt().coerceAtMost(canyonCount - 1)
                val idx = variantIndices[valleyCount + variant]
                idx.add(v00); idx.add(v01); idx.add(v11); idx.add(v00); idx.add(v11); idx.add(v10)
            } else {
                val variant = (WorldLayout.hash01(i, j, 7.3f) * valleyCount).toInt().coerceAtMost(valleyCount - 1)
                val idx = variantIndices[variant]
                idx.add(v00); idx.add(v01); idx.add(v11); idx.add(v00); idx.add(v11); idx.add(v10)
            }
        }
        val vb = newVertexBuffer(positions, normals)
        // The bounds must cover the whole strip from the hollow to the canyon floor.
        val bounds = Box(
            floatArrayOf((x0 + x1) / 2f, 1.5f, (z0 + z1) / 2f),
            floatArrayOf((x1 - x0) / 2f + 2f, 8f, (z1 - z0) / 2f + 2f),
        )
        return variantIndices.map { idx ->
            registerMesh(vb, newIndexBuffer(idx), idx.size, bounds)
        }
    }

    // ---- v2.3 primitive builders --------------------------------------------

    /** Flat-shaded triangle into shared growable buffers (winding as given). */
    private fun emitTri(
        positions: ArrayList<Float>, normals: ArrayList<Float>, indices: ArrayList<Int>,
        a: FloatArray, b: FloatArray, c: FloatArray,
    ) {
        val e1x = b[0]-a[0]; val e1y = b[1]-a[1]; val e1z = b[2]-a[2]
        val e2x = c[0]-a[0]; val e2y = c[1]-a[1]; val e2z = c[2]-a[2]
        val nx = e1y*e2z - e1z*e2y
        val ny = e1z*e2x - e1x*e2z
        val nz = e1x*e2y - e1y*e2x
        val len = sqrt(nx*nx + ny*ny + nz*nz).coerceAtLeast(1e-8f)
        val base = positions.size / 3
        for (p in arrayOf(a, b, c)) {
            positions.add(p[0]); positions.add(p[1]); positions.add(p[2])
            normals.add(nx/len); normals.add(ny/len); normals.add(nz/len)
        }
        indices.add(base); indices.add(base+1); indices.add(base+2)
    }

    /**
     * Emits one subdivided quad per face with a per-vertex warp. The warp is
     * a PURE function of position, so vertices shared between two faces land
     * on the same warped point and the mesh stays watertight — the same trick
     * buildRock's lattice jitter relies on.
     */
    private fun buildGridMesh(
        faces: Array<Array<FloatArray>>, n: Int,
        warp: (FloatArray) -> FloatArray, bounds: Box,
    ): Mesh {
        val positions = ArrayList<Float>(768); val normals = ArrayList<Float>(768); val indices = ArrayList<Int>(512)
        for (quad in faces) {
            val pts = Array(n) { i -> Array(n) { j ->
                val u = i.toFloat()/(n-1); val v = j.toFloat()/(n-1)
                val p = FloatArray(3)
                for (k in 0..2) p[k] = quad[0][k]*(1-u)*(1-v)+quad[1][k]*u*(1-v)+quad[2][k]*u*v+quad[3][k]*(1-u)*v
                warp(p)
            }}
            for (i in 0 until n-1) for (j in 0 until n-1) {
                val a=pts[i][j]; val b=pts[i+1][j]; val c=pts[i+1][j+1]; val d=pts[i][j+1]
                val nx=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1])
                val ny=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2])
                val nz=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
                val len = sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(1e-8f)
                val base = positions.size/3
                for (p in arrayOf(a,b,c,d)) {
                    positions.add(p[0]); positions.add(p[1]); positions.add(p[2])
                    normals.add(nx/len); normals.add(ny/len); normals.add(nz/len)
                }
                indices.add(base); indices.add(base+1); indices.add(base+2)
                indices.add(base); indices.add(base+2); indices.add(base+3)
            }
        }
        return registerMesh(newVertexBuffer(positions.toFloatArray(), normals.toFloatArray()), newIndexBuffer(indices), indices.size, bounds)
    }

    /** The 5 visible faces of the unit box (no bottom), 4 corners each. */
    private fun boxFaceCorners(): Array<Array<FloatArray>> {
        val h = 0.5f
        return arrayOf(
            arrayOf(floatArrayOf(-h,0f,h), floatArrayOf(h,0f,h), floatArrayOf(h,1f,h), floatArrayOf(-h,1f,h)),
            arrayOf(floatArrayOf(h,0f,h), floatArrayOf(h,0f,-h), floatArrayOf(h,1f,-h), floatArrayOf(h,1f,h)),
            arrayOf(floatArrayOf(h,0f,-h), floatArrayOf(-h,0f,-h), floatArrayOf(-h,1f,-h), floatArrayOf(h,1f,-h)),
            arrayOf(floatArrayOf(-h,0f,-h), floatArrayOf(-h,0f,h), floatArrayOf(-h,1f,h), floatArrayOf(-h,1f,-h)),
            arrayOf(floatArrayOf(-h,1f,h), floatArrayOf(h,1f,h), floatArrayOf(h,1f,-h), floatArrayOf(-h,1f,-h)),
        )
    }

    private fun buildRoundedBox(): Mesh {
        // Chamfered cube: clamp to the inner box, then push the offset back
        // out to a fixed corner radius. Face centres are untouched, so it
        // still stacks flush like a box — only the silhouette softens.
        val rr = 0.18f
        return buildGridMesh(boxFaceCorners(), 6, { p ->
            val lim = 0.5f - rr
            val qx = p[0].coerceIn(-lim, lim); val qy = p[1].coerceIn(-lim, lim); val qz = p[2].coerceIn(-lim, lim)
            val dx = p[0]-qx; val dy = p[1]-qy; val dz = p[2]-qz
            val len = sqrt(dx*dx+dy*dy+dz*dz)
            if (len < 1e-5f) p else floatArrayOf(qx+rr*dx/len, qy+rr*dy/len, qz+rr*dz/len)
        }, Box(floatArrayOf(0f,0.5f,0f), floatArrayOf(0.5f,0.5f,0.5f)))
    }

    private fun buildPrism(sides: Int, topScale: Float): Mesh {
        val positions = ArrayList<Float>(256); val normals = ArrayList<Float>(256); val indices = ArrayList<Int>(192)
        val twoPi = (2.0 * Math.PI).toFloat()
        val bottom = Array(sides) { i ->
            val a = i * twoPi / sides
            floatArrayOf(cos(a)*0.5f, 0f, sin(a)*0.5f)
        }
        val top = Array(sides) { i ->
            val a = i * twoPi / sides
            floatArrayOf(cos(a)*0.5f*topScale, 1f, sin(a)*0.5f*topScale)
        }
        for (i in 0 until sides) {
            val j = (i+1) % sides
            // Side quad (b_i, t_i, t_j, b_j): outward winding.
            emitTri(positions, normals, indices, bottom[i], top[i], top[j])
            emitTri(positions, normals, indices, bottom[i], top[j], bottom[j])
        }
        if (topScale > 0.05f) {
            val c = floatArrayOf(0f, 1f, 0f)
            for (i in 0 until sides) emitTri(positions, normals, indices, c, top[(i+1)%sides], top[i])
        }
        val cb = floatArrayOf(0f, 0f, 0f)
        for (i in 0 until sides) emitTri(positions, normals, indices, cb, bottom[i], bottom[(i+1)%sides])
        return registerMesh(newVertexBuffer(positions.toFloatArray(), normals.toFloatArray()), newIndexBuffer(indices), indices.size, Box(floatArrayOf(0f,0.5f,0f), floatArrayOf(0.5f,0.5f,0.5f)))
    }

    private fun buildDome(): Mesh {
        val seg = 8; val rings = 4; val r = 0.5f
        val positions = FloatArray((rings+1)*(seg+1)*3)
        val normals = FloatArray((rings+1)*(seg+1)*3)
        var v = 0
        for (i in 0..rings) {
            val polar = Math.PI / 2.0 * i / rings
            val sp = sin(polar).toFloat(); val cp = cos(polar).toFloat()
            for (j in 0..seg) {
                val az = 2.0 * Math.PI * j / seg
                val sa = sin(az).toFloat(); val ca = cos(az).toFloat()
                positions[v*3] = r*sp*ca; positions[v*3+1] = r*cp; positions[v*3+2] = r*sp*sa
                normals[v*3] = sp*ca; normals[v*3+1] = cp; normals[v*3+2] = sp*sa
                v++
            }
        }
        val indices = ArrayList<Int>(seg*rings*6)
        for (i in 0 until rings) for (j in 0 until seg) {
            val a = i*(seg+1)+j; val b = a+seg+1
            indices.add(a); indices.add(a+1); indices.add(b+1)
            indices.add(a); indices.add(b+1); indices.add(b)
        }
        return registerMesh(newVertexBuffer(positions, normals), newIndexBuffer(indices), indices.size, Box(floatArrayOf(0f,0.25f,0f), floatArrayOf(0.5f,0.25f,0.5f)))
    }

    private fun buildGem(): Mesh {
        val positions = ArrayList<Float>(96); val normals = ArrayList<Float>(96); val indices = ArrayList<Int>(24)
        val ring = Array(4) { i ->
            val a = Math.PI/4 + i * Math.PI/2
            floatArrayOf(cos(a).toFloat()*0.5f, 0.4f, sin(a).toFloat()*0.5f)
        }
        val top = floatArrayOf(0f, 1f, 0f)
        val bot = floatArrayOf(0f, 0f, 0f)
        for (i in 0 until 4) {
            val j = (i+1) % 4
            emitTri(positions, normals, indices, ring[i], top, ring[j])
            emitTri(positions, normals, indices, ring[i], ring[j], bot)
        }
        return registerMesh(newVertexBuffer(positions.toFloatArray(), normals.toFloatArray()), newIndexBuffer(indices), indices.size, Box(floatArrayOf(0f,0.5f,0f), floatArrayOf(0.5f,0.5f,0.5f)))
    }

    private fun buildArchPanel(): Mesh {
        val w = 0.95f; val h = 0.60f
        val cols = 7; val rows = 4
        val positions = ArrayList<Float>(256); val normals = ArrayList<Float>(256); val indices = ArrayList<Int>(192)
        fun topAt(x: Float): Float {
            val k = (2f*x/w).coerceIn(-1f, 1f)
            return h * (0.55f + 0.45f * sqrt(1f - k*k))
        }
        for (i in 0 until cols) {
            val x0 = -w/2f + w*i/cols
            val x1 = -w/2f + w*(i+1)/cols
            val t0 = topAt(x0); val t1 = topAt(x1)
            for (j in 0 until rows) {
                val y00 = t0*j/rows; val y01 = t0*(j+1)/rows
                val y10 = t1*j/rows; val y11 = t1*(j+1)/rows
                // +z facing quad, CCW seen from the front.
                val a = floatArrayOf(x0, y00, 0f); val b = floatArrayOf(x1, y10, 0f)
                val c = floatArrayOf(x1, y11, 0f); val d = floatArrayOf(x0, y01, 0f)
                emitTri(positions, normals, indices, a, b, c)
                emitTri(positions, normals, indices, a, c, d)
            }
        }
        return registerMesh(newVertexBuffer(positions.toFloatArray(), normals.toFloatArray()), newIndexBuffer(indices), indices.size, Box(floatArrayOf(0f,0.3f,0f), floatArrayOf(0.5f,0.31f,0.06f)))
    }

    private fun buildSail(): Mesh {
        val y0 = 0.2f; val y1 = 2.5f
        val bands = 5; val halfRoot = 0.24f; val halfTip = 0.12f; val t = 0.045f
        val positions = ArrayList<Float>(256); val normals = ArrayList<Float>(256); val indices = ArrayList<Int>(192)
        for (k in 0 until bands) {
            val f0 = k.toFloat()/bands; val f1 = (k+1).toFloat()/bands
            val ya = y0 + (y1-y0)*f0; val yb = y0 + (y1-y0)*f1
            val wa = halfRoot + (halfTip-halfRoot)*f0; val wb = halfRoot + (halfTip-halfRoot)*f1
            // +z face
            emitTri(positions, normals, indices, floatArrayOf(-t,ya,wa), floatArrayOf(t,ya,wa), floatArrayOf(t,yb,wb))
            emitTri(positions, normals, indices, floatArrayOf(-t,ya,wa), floatArrayOf(t,yb,wb), floatArrayOf(-t,yb,wb))
            // -z face
            emitTri(positions, normals, indices, floatArrayOf(t,ya,-wa), floatArrayOf(-t,ya,-wa), floatArrayOf(-t,yb,-wb))
            emitTri(positions, normals, indices, floatArrayOf(t,ya,-wa), floatArrayOf(-t,yb,-wb), floatArrayOf(t,yb,-wb))
            // +x edge
            emitTri(positions, normals, indices, floatArrayOf(t,ya,wa), floatArrayOf(t,ya,-wa), floatArrayOf(t,yb,-wb))
            emitTri(positions, normals, indices, floatArrayOf(t,ya,wa), floatArrayOf(t,yb,-wb), floatArrayOf(t,yb,wb))
            // -x edge
            emitTri(positions, normals, indices, floatArrayOf(-t,ya,-wa), floatArrayOf(-t,ya,wa), floatArrayOf(-t,yb,wb))
            emitTri(positions, normals, indices, floatArrayOf(-t,ya,-wa), floatArrayOf(-t,yb,wb), floatArrayOf(-t,yb,-wb))
        }
        // Tip cap.
        emitTri(positions, normals, indices, floatArrayOf(-t,y1,halfTip), floatArrayOf(t,y1,halfTip), floatArrayOf(t,y1,-halfTip))
        emitTri(positions, normals, indices, floatArrayOf(-t,y1,halfTip), floatArrayOf(t,y1,-halfTip), floatArrayOf(-t,y1,-halfTip))
        return registerMesh(newVertexBuffer(positions.toFloatArray(), normals.toFloatArray()), newIndexBuffer(indices), indices.size, Box(floatArrayOf(0f,1.35f,0f), floatArrayOf(0.06f,1.25f,0.28f)))
    }

    private fun buildCanopyBlob(seed: Int): Mesh {
        val seg = 8; val rings = 5
        val faces = ArrayList<Array<FloatArray>>(seg*rings)
        for (i in 0 until rings) for (j in 0 until seg) {
            fun pt(ii: Int, jj: Int): FloatArray {
                val polar = Math.PI * ii / rings
                val az = 2.0 * Math.PI * jj / seg
                return floatArrayOf(
                    (0.5f * sin(polar) * cos(az)).toFloat(),
                    (0.5f * cos(polar)).toFloat(),
                    (0.5f * sin(polar) * sin(az)).toFloat(),
                )
            }
            faces.add(arrayOf(pt(i,j), pt(i,j+1), pt(i+1,j+1), pt(i+1,j)))
        }
        // Radial jitter keyed on the vertex lattice — pure function of p, so
        // shared corners stay welded and the blob stays watertight.
        return buildGridMesh(faces.toTypedArray(), 2, { p ->
            val len = sqrt(p[0]*p[0]+p[1]*p[1]+p[2]*p[2]).coerceAtLeast(1e-6f)
            val ix = Math.round(p[0]*6f); val iy = Math.round(p[1]*6f); val iz = Math.round(p[2]*6f)
            val amt = (hash3(ix, iy, iz, seed*17+5) - 0.5f) * 0.13f
            floatArrayOf(p[0] + p[0]/len*amt, p[1] + p[1]/len*amt, p[2] + p[2]/len*amt)
        }, Box(floatArrayOf(0f,0f,0f), floatArrayOf(0.62f,0.62f,0.62f)))
    }

    private fun buildCliffSlab(seed: Int): Mesh {
        // A box-like slab that tapers toward a craggy, jittered top — the
        // canyon walls stop reading as stacked shipping containers.
        return buildGridMesh(boxFaceCorners(), 4, { p ->
            val up = (p[1] + 0.5f).coerceIn(0f, 1f)
            val shrink = 1f - 0.22f*up
            val ix = Math.round(p[0]*3f); val iy = Math.round(p[1]*3f); val iz = Math.round(p[2]*3f)
            val jx = (hash3(ix, iy, iz, seed*13+1) - 0.5f) * 0.34f
            val jy = (hash3(ix, iy, iz, seed*13+2) - 0.5f) * 0.22f
            val jz = (hash3(ix, iy, iz, seed*13+3) - 0.5f) * 0.34f
            floatArrayOf(p[0]*shrink + jx, p[1] + jy, p[2]*shrink + jz)
        }, Box(floatArrayOf(0f,0.5f,0f), floatArrayOf(0.85f,0.65f,0.85f)))
    }

    private fun buildGableRoof(w: Float, d: Float, pitch: Float, overhang: Float): Mesh {
        val d2 = d/2f + overhang
        val rise = (d/2f) * tan(pitch) + 0.08f
        val x0 = -w/2f - overhang; val x1 = w/2f + overhang
        // Closed profile in (z, y), traversed so side normals face outward.
        val profile = arrayOf(
            floatArrayOf(-d2, 0f),   // left eave
            floatArrayOf(-0.10f, rise), // left ridge cap edge
            floatArrayOf(0.10f, rise),  // right ridge cap edge
            floatArrayOf(d2, 0f),    // right eave
        )
        val order = intArrayOf(0, 3, 2, 1)  // eave L -> eave R -> ridge R -> ridge L
        val positions = ArrayList<Float>(96); val normals = ArrayList<Float>(96); val indices = ArrayList<Int>(48)
        for (k in order.indices) {
            val a = profile[order[k]]; val b = profile[order[(k+1) % order.size]]
            // Side quad extruded along X.
            emitTri(positions, normals, indices,
                floatArrayOf(x0, a[1], a[0]), floatArrayOf(x1, a[1], a[0]), floatArrayOf(x1, b[1], b[0]))
            emitTri(positions, normals, indices,
                floatArrayOf(x0, a[1], a[0]), floatArrayOf(x1, b[1], b[0]), floatArrayOf(x0, b[1], b[0]))
        }
        // End caps (both gable ends), profile order 0,1,2,3.
        for (x in floatArrayOf(x0, x1)) {
            val c0 = floatArrayOf(x, profile[0][1], profile[0][0])
            val c1 = floatArrayOf(x, profile[1][1], profile[1][0])
            val c2 = floatArrayOf(x, profile[2][1], profile[2][0])
            val c3 = floatArrayOf(x, profile[3][1], profile[3][0])
            if (x == x1) {
                emitTri(positions, normals, indices, c0, c1, c2)
                emitTri(positions, normals, indices, c0, c2, c3)
            } else {
                emitTri(positions, normals, indices, c0, c2, c1)
                emitTri(positions, normals, indices, c0, c3, c2)
            }
        }
        val halfX = w/2f + overhang + 0.15f
        return registerMesh(newVertexBuffer(positions.toFloatArray(), normals.toFloatArray()), newIndexBuffer(indices), indices.size, Box(floatArrayOf(0f, rise/2f, 0f), floatArrayOf(halfX, rise/2f + 0.15f, d2 + 0.15f)))
    }

    /** A thin 3-sided grass blade leaning a little. */
    private fun emitSpike(
        positions: ArrayList<Float>, normals: ArrayList<Float>, indices: ArrayList<Int>,
        cx: Float, cy: Float, cz: Float, r: Float, h: Float, yaw: Float, tilt: Float,
    ) {
        val apex = floatArrayOf(cx + sin(yaw)*tilt*h*0.5f, cy + h, cz + cos(yaw)*tilt*h*0.5f)
        for (k in 0 until 3) {
            val a0 = yaw + k * 2.0943951f
            val a1 = yaw + (k+1) * 2.0943951f
            val b0 = floatArrayOf(cx + cos(a0)*r, cy, cz + sin(a0)*r)
            val b1 = floatArrayOf(cx + cos(a1)*r, cy, cz + sin(a1)*r)
            emitTri(positions, normals, indices, b0, apex, b1)
        }
    }

    /** A little 4-sided rock chip. */
    private fun emitPyramid(
        positions: ArrayList<Float>, normals: ArrayList<Float>, indices: ArrayList<Int>,
        cx: Float, cy: Float, cz: Float, r: Float, h: Float, yaw: Float,
    ) {
        val apex = floatArrayOf(cx, cy + h, cz)
        for (k in 0 until 4) {
            val a0 = yaw + k * 1.5707964f
            val a1 = yaw + (k+1) * 1.5707964f
            val b0 = floatArrayOf(cx + cos(a0)*r, cy, cz + sin(a0)*r)
            val b1 = floatArrayOf(cx + cos(a1)*r, cy, cz + sin(a1)*r)
            emitTri(positions, normals, indices, b0, apex, b1)
        }
    }

    private fun buildScatter(pebbles: Boolean): Mesh {
        val rng = java.util.Random(if (pebbles) 8181 else 5150)
        val positions = ArrayList<Float>(3072); val normals = ArrayList<Float>(3072); val indices = ArrayList<Int>(2304)
        val target = if (pebbles) 70 else 170
        var placed = 0
        var attempts = 0
        while (placed < target && attempts < 6000) {
            attempts++
            val x = WorldLayout.PLAY_X_MIN + 1.5f + rng.nextFloat() * (WorldLayout.PLAY_X_MAX - WorldLayout.PLAY_X_MIN - 3f)
            val z = WorldLayout.PLAY_Z_MIN + 1.5f + rng.nextFloat() * (WorldLayout.PLAY_Z_MAX - WorldLayout.PLAY_Z_MIN - 3f)
            if (WorldLayout.corridorOutsideDistance(x, z) > 3.5f) continue
            if (WorldLayout.dist(x, z, WorldLayout.SPAWN_X, WorldLayout.SPAWN_Z) < 3.2f) continue
            if (WorldLayout.dist(x, z, WorldLayout.TRADE_POST_X, WorldLayout.TRADE_POST_Z) < 4.2f) continue
            if (WorldLayout.dist(x, z, WorldLayout.BIN_X, WorldLayout.BIN_Z) < 2.6f) continue
            if (WorldLayout.dist(x, z, WorldLayout.FURNACE_X, WorldLayout.FURNACE_Z) < 3.4f) continue
            if (WorldLayout.dist(x, z, WorldLayout.ANVIL_X, WorldLayout.ANVIL_Z) < 2.6f) continue
            if (WorldLayout.dist(x, z, 0f, WorldLayout.GATE_Z) < 6.5f) continue
            if (WorldLayout.dist(x, z, WorldLayout.ROAD_SOUTH_X, WorldLayout.ROAD_SOUTH_Z) < 3.2f) continue
            if (WorldLayout.dist(x, z, Town.WELL_X, Town.WELL_Z) < 5f) continue
            if (Town.slots.any { WorldLayout.dist(x, z, it.x, it.z) < 3.4f }) continue
            val y = WorldLayout.groundHeight(x, z)
            if (pebbles) {
                val s = 0.06f + rng.nextFloat() * 0.07f
                emitPyramid(positions, normals, indices, x, y - 0.02f, z, s, s * (0.5f + rng.nextFloat() * 0.4f), rng.nextFloat() * 6.2831853f)
            } else {
                for (b in 0 until 2) {
                    emitSpike(
                        positions, normals, indices,
                        x + (rng.nextFloat()-0.5f)*0.5f, y - 0.02f, z + (rng.nextFloat()-0.5f)*0.5f,
                        0.05f, 0.14f + rng.nextFloat() * 0.16f,
                        rng.nextFloat() * 6.2831853f, (rng.nextFloat()-0.5f) * 0.35f,
                    )
                }
            }
            placed++
        }
        val x0 = WorldLayout.PLAY_X_MIN; val x1 = WorldLayout.PLAY_X_MAX
        val z0 = WorldLayout.PLAY_Z_MIN; val z1 = WorldLayout.PLAY_Z_MAX
        val bounds = Box(
            floatArrayOf((x0+x1)/2f, 1.5f, (z0+z1)/2f),
            floatArrayOf((x1-x0)/2f + 3f, 9f, (z1-z0)/2f + 3f),
        )
        return registerMesh(newVertexBuffer(positions.toFloatArray(), normals.toFloatArray()), newIndexBuffer(indices), indices.size, bounds)
    }

    /**
     * Filament has no NORMAL vertex attribute: LIT materials consume the TANGENTS
     * attribute (tangent frame packed as a quaternion, w carries handedness).
     * We encode each per-face normal as the shortest-arc rotation from +Z onto
     * the normal, which yields a valid TBN whose N column is the normal.
     */
    private fun newVertexBuffer(positions: FloatArray, normals: FloatArray): VertexBuffer {
        val vertexCount = positions.size / 3
        val floatsPerVertex = 7 // pos(3) + tangent quaternion(4)
        val vb = VertexBuffer.Builder()
            .bufferCount(1).vertexCount(vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 28)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, 28)
            .build(engine)
        val buf = ByteBuffer.allocateDirect(vertexCount * floatsPerVertex * 4).order(ByteOrder.nativeOrder())
        val packed = buf.asFloatBuffer()
        var i = 0
        while (i < positions.size) {
            packed.put(positions[i]); packed.put(positions[i+1]); packed.put(positions[i+2])
            val quat = normalToTangentQuat(normals[i], normals[i+1], normals[i+2])
            packed.put(quat[0]); packed.put(quat[1]); packed.put(quat[2]); packed.put(quat[3])
            i += 3
        }
        vb.setBufferAt(engine, 0, buf)
        return vb
    }

    private fun normalToTangentQuat(nx: Float, ny: Float, nz: Float): FloatArray {
        // shortest-arc quaternion rotating +Z onto n: (cross((0,0,1), n), 1 + dot)
        var x: Float; var y: Float; var z: Float; var w: Float
        if (nz >= 0.99999f) { x = 0f; y = 0f; z = 0f; w = 1f }
        else if (nz <= -0.99999f) { x = 1f; y = 0f; z = 0f; w = 0f }
        else {
            x = -ny; y = nx; z = 0f; w = 1f + nz
            val len = sqrt(x*x + y*y + z*z + w*w)
            x /= len; y /= len; z /= len; w /= len
        }
        return floatArrayOf(x, y, z, w)
    }

    private fun newIndexBuffer(indices: List<Int>): IndexBuffer {
        val ib = IndexBuffer.Builder().indexCount(indices.size).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
        val buf = ByteBuffer.allocateDirect(indices.size*2).order(ByteOrder.nativeOrder())
        for (i in indices) buf.putShort(i.toShort())
        buf.flip()
        ib.setBuffer(engine, buf)
        return ib
    }

    private fun registerMesh(vb: VertexBuffer, ib: IndexBuffer, indexCount: Int, bounds: Box): Mesh {
        val mesh = Mesh(vb, ib, indexCount, bounds); meshes.add(mesh); return mesh
    }

    private fun hash3(x: Int, y: Int, z: Int, seed: Int): Float {
        var h = x*374761393+y*668265263+z*1274126177+seed*97
        h = (h xor (h shr 13))*1274126177; h = h xor (h shr 16)
        return (h and 0xFFFF).toFloat()/65535f
    }

    fun destroy(scene: Scene) {
        // Idempotent + exception-hardened teardown. A crash here used to kill
        // the whole process at activity destroy (which fires mid-transition
        // when the slot picker relaunches the activity), leaving the new
        // activity dead on its loading screen.
        if (teardownDone) return
        teardownDone = true
        for (entity in entities) {
            runCatching { scene.removeEntity(entity) }
            runCatching { engine.destroyEntity(entity) }
        }
        entities.clear()
        for (instance in materialInstances) runCatching { engine.destroyMaterialInstance(instance) }
        materialInstances.clear()
        litMaterial?.let { runCatching { engine.destroyMaterial(it) } }
        smokeMaterial?.let { runCatching { engine.destroyMaterial(it) } }
        litMaterial = null
        smokeMaterial = null
        // CRITICAL: terrain color variants share ONE VertexBuffer — destroy
        // each unique GPU buffer exactly once. Calling destroyVertexBuffer()
        // on an already-destroyed buffer throws IllegalStateException and
        // crashed every activity teardown before this dedupe existed.
        val deadVertexBuffers = HashSet<VertexBuffer>()
        val deadIndexBuffers = HashSet<IndexBuffer>()
        for (mesh in meshes) {
            if (deadVertexBuffers.add(mesh.vertexBuffer)) runCatching { engine.destroyVertexBuffer(mesh.vertexBuffer) }
            if (deadIndexBuffers.add(mesh.indexBuffer)) runCatching { engine.destroyIndexBuffer(mesh.indexBuffer) }
        }
        meshes.clear()
    }
}

/**
 * Locked isometric camera: yaw and pitch are fixed at 45° so the world
 * always reads as a classic isometric diorama. One-finger drag pans the
 * view WITH the finger (the terrain slides the opposite way), pinch zooms.
 */
class CameraRig(private val engine: Engine) {
    val camera: Camera
    private val cameraEntity: Int = EntityManager.get().create()
    private var focusX = WorldLayout.SPAWN_X
    private var focusZ = WorldLayout.SPAWN_Z
    private var targetFocusX = focusX
    private var targetFocusZ = focusZ
    private var zoom = 15f
    private var targetZoom = zoom
    private val yaw = Math.toRadians(Theme.CAMERA_YAW_DEGREES.toDouble()).toFloat()
    private val pitch = Math.toRadians(Theme.CAMERA_PITCH_DEGREES.toDouble()).toFloat()
    private var viewportWidth = 1
    private var viewportHeight = 1

    init {
        camera = engine.createCamera(cameraEntity)
        camera.setExposure(16f, 1f/125f, 100f)
    }

    fun setViewport(width: Int, height: Int) { viewportWidth = width; viewportHeight = height }

    /** TEMPORARY lighting calibration probe. */
    fun setProbeExposure(aperture: Float, shutter: Float, iso: Float) {
        camera.setExposure(aperture, shutter, iso)
    }

    fun setExposure(aperture: Float, shutter: Float, iso: Float) {
        camera.setExposure(aperture, shutter, iso)
    }

    fun panByPixels(dxPx: Float, dyPx: Float) {
        val worldPerPixel = 2f*zoom/viewportHeight
        val dx = dxPx*worldPerPixel; val dy = dyPx*worldPerPixel
        val s = sin(yaw); val c = cos(yaw)
        // Camera-attached panning: the focus follows the finger on screen, so
        // dragging up pans the view north (the ground slides DOWN, opposite
        // the finger) — the inverted feel requested for v2.1.
        targetFocusX += dx*c + dy*s
        targetFocusZ += -dx*s + dy*c
        clampFocus()
    }

    /** Pinch-out (factor > 1) must zoom IN, i.e. shrink the visible world span. */
    fun zoomBy(factor: Float) { targetZoom = (targetZoom / factor).coerceIn(8f, 36f) }

    fun update(dt: Float) {
        val panLerp = 1f - exp(-12f*dt)
        val zoomLerp = 1f - exp(-10f*dt)
        focusX += (targetFocusX-focusX)*panLerp
        focusZ += (targetFocusZ-focusZ)*panLerp
        zoom += (targetZoom-zoom)*zoomLerp
        apply()
    }

    private fun apply() {
        val cy = cos(yaw)*cos(pitch)
        val sy = sin(yaw)*cos(pitch)
        val py = sin(pitch)
        camera.lookAt(
            (focusX+sy*80f).toDouble(), (py*80f).toDouble(), (focusZ+cy*80f).toDouble(),
            focusX.toDouble(), 0.0, focusZ.toDouble(), 0.0, 1.0, 0.0,
        )
        val aspect = viewportWidth.toFloat()/viewportHeight.toFloat()
        camera.setProjection(
            Camera.Projection.ORTHO,
            (-zoom*aspect).toDouble(), (zoom*aspect).toDouble(), -zoom.toDouble(), zoom.toDouble(), 1.0, 200.0,
        )
    }

    private fun clampFocus() {
        // X spans the hollow in the west through the valley's east rim; Z the
        // canyon depth through the south meadow.
        targetFocusX = targetFocusX.coerceIn(-42f, 24f)
        targetFocusZ = targetFocusZ.coerceIn(-40f, 16f)
    }

    /** v2.2 — where the camera looks; the rain column follows it. */
    fun focus(): FloatArray = floatArrayOf(focusX, focusZ)

    fun screenToGround(px: Float, py: Float): FloatArray {
        val s = sin(yaw); val c = cos(yaw)
        val sp = sin(pitch); val cp = cos(pitch)
        val halfHeight = zoom
        val halfWidth = zoom*viewportWidth/viewportHeight.toFloat()
        val eyeX = focusX+s*cp*80f; val eyeY = sp*80f; val eyeZ = focusZ+c*cp*80f
        val fwdX = -s*cp; val fwdY = -sp; val fwdZ = -c*cp
        val ndcX = 2f*px/viewportWidth-1f
        val ndcY = 1f-2f*py/viewportHeight
        val pX = eyeX+c*(ndcX*halfWidth)-s*sp*(ndcY*halfHeight)
        val pY = eyeY+cp*(ndcY*halfHeight)
        val pZ = eyeZ-s*(ndcX*halfWidth)-c*sp*(ndcY*halfHeight)
        var t = -pY/fwdY
        var x = pX+fwdX*t; var z = pZ+fwdZ*t
        t = (WorldLayout.groundHeight(x,z)-pY)/fwdY
        x = pX+fwdX*t; z = pZ+fwdZ*t
        return floatArrayOf(
            x.coerceIn(WorldLayout.PLAY_X_MIN, WorldLayout.PLAY_X_MAX),
            z.coerceIn(WorldLayout.PLAY_Z_MIN, WorldLayout.PLAY_Z_MAX),
        )
    }

    fun projectToScreen(x: Float, z: Float): FloatArray {
        val s = sin(yaw); val c = cos(yaw)
        val sp = sin(pitch); val cp = cos(pitch)
        val halfHeight = zoom
        val halfWidth = zoom*viewportWidth/viewportHeight.toFloat()
        val eyeX = focusX+s*cp*80f; val eyeY = sp*80f; val eyeZ = focusZ+c*cp*80f
        val fwdX = -s*cp; val fwdY = -sp; val fwdZ = -c*cp
        val t = (x-eyeX)/fwdX
        val dx = fwdX*t; val dy = fwdY*t; val dz = fwdZ*t
        val ndcX = (dx*c+dz*-s)/halfWidth
        val ndcY = (dx*-s*sp+dy*cp+dz*-c*sp)/halfHeight
        return floatArrayOf((ndcX+1f)/2f*viewportWidth, (1f-ndcY)/2f*viewportHeight)
    }

    fun destroy() { engine.destroyCameraComponent(cameraEntity); engine.destroyEntity(cameraEntity) }
}

class FilamentHost {
    companion object {
        init {
            // CRITICAL: loads libfilament-jni.so before any Filament API is used.
            // Without this, Engine.create() throws UnsatisfiedLinkError and the
            // app crashes immediately on launch.
            Filament.init()
        }

        /**
         * Creates the rendering engine with a device-compatibility fallback:
         * OpenGL is Filament's default; on devices whose GLES3 driver fails to
         * initialize we retry with the Vulkan backend before giving up.
         */
        fun createEngine(): Engine = try {
            Engine.create()
        } catch (gl: Throwable) {
            try {
                Engine.create(Engine.Backend.VULKAN)
            } catch (vk: Throwable) {
                val err = RuntimeException(
                    "Filament engine failed to start on both OpenGL and Vulkan backends"
                )
                err.addSuppressed(gl)
                err.addSuppressed(vk)
                throw err
            }
        }
    }

    val engine: Engine = createEngine()
    private val renderer: Renderer = engine.createRenderer()
    private val view: View = engine.createView()
    private var swapChain: SwapChain? = null
    private var world: WorldRenderer? = null
    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var lastFrameNanos = 0L
    private var destroyed = false

    /** Flips true after the first successfully presented frame (drives the loading screen). */
    val firstFrameRendered = MutableStateFlow(false)

    fun bind(world: WorldRenderer) {
        this.world = world
        view.scene = world.scene
        view.camera = world.camera
    }

    /** TEMPORARY lighting calibration probe. */
    fun setToneMapping(linear: Boolean) {
        view.toneMapping = if (linear) View.ToneMapping.LINEAR else View.ToneMapping.ACES
    }

    fun onSurfaceAvailable(surface: Surface) {
        if (destroyed) return
        swapChain = engine.createSwapChain(surface)
        start()
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        if (destroyed) return
        view.viewport = Viewport(0, 0, width, height)
        world?.onViewport(width, height)
    }

    fun onSurfaceDestroyed() {
        if (destroyed) return
        stop()
        swapChain?.let { runCatching { engine.destroySwapChain(it) } }
        swapChain = null
    }

    fun start() {
        if (destroyed || running) return
        running = true; lastFrameNanos = 0L; choreographer.postFrameCallback(frameCallback)
    }

    fun stop() { running = false; choreographer.removeFrameCallback(frameCallback) }

    private val frameCallback: Choreographer.FrameCallback = Choreographer.FrameCallback { frameTimeNanos: Long ->
        val dt = if (lastFrameNanos == 0L) 0f else (frameTimeNanos-lastFrameNanos)*1e-9f
        lastFrameNanos = frameTimeNanos
        draw(dt, frameTimeNanos)
        if (running) choreographer.postFrameCallback(frameCallback)
    }

    private fun draw(dt: Float, frameTimeNanos: Long) {
        if (destroyed) return
        val chain = swapChain ?: return
        val w = world ?: return
        w.update(dt)
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            if (!firstFrameRendered.value) firstFrameRendered.value = true
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        stop()
        swapChain?.let { runCatching { engine.destroySwapChain(it) } }
        swapChain = null
        // The SurfaceView outlives this call while the activity finishes, so
        // surface callbacks keep arriving after the engine is gone — all
        // entry points above are guarded by `destroyed`.
        runCatching { engine.destroyView(view) }
        runCatching { engine.destroyRenderer(renderer) }
        runCatching { engine.destroy() }
    }
}
