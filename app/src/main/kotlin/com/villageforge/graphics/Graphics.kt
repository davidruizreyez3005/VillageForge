package com.villageforge.graphics

import android.view.Choreographer
import android.view.Surface
import com.google.android.filament.*
import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialPackage
import com.villageforge.config.Theme
import com.villageforge.config.WorldLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

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
    fun rootInto(out: FloatArray, tx: Float, ty: Float, tz: Float, yawRadians: Float) {
        val c = cos(yawRadians); val s = sin(yawRadians)
        out[0]=c; out[1]=0f; out[2]=-s; out[3]=0f
        out[4]=0f; out[5]=1f; out[6]=0f; out[7]=0f
        out[8]=s; out[9]=0f; out[10]=c; out[11]=0f
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

    init { MaterialBuilder.init() }

    private fun litMaterialDef(): Material {
        if (litMaterial == null) {
            val pkg = MaterialBuilder()
                .name("vf_lit")
                .shading(MaterialBuilder.Shading.LIT)
                .materialDomain(MaterialBuilder.MaterialDomain.SURFACE)
                .blending(MaterialBuilder.BlendingMode.OPAQUE)
                .culling(MaterialBuilder.CullingMode.BACK)
                .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "baseColor")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughness")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "metallic")
                .build(engine)
            litMaterial = Material.Builder()
                .payload(pkg.buffer, pkg.buffer.remaining())
                .build(engine)
        }
        return litMaterial!!
    }

    fun material(color: Theme.Rgb, roughness: Float, metallic: Float = Theme.METALLIC_DEFAULT): MaterialInstance {
        val instance = litMaterialDef().createInstance()
        instance.setParameter("baseColor", color.r, color.g, color.b)
        instance.setParameter("roughness", roughness)
        instance.setParameter("metallic", metallic)
        materialInstances.add(instance)
        return instance
    }

    fun addRenderable(scene: Scene, mesh: Mesh, instance: MaterialInstance, transform: FloatArray): Int {
        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(mesh.bounds)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, mesh.vertexBuffer, mesh.indexBuffer, 0, mesh.indexCount)
            .material(0, instance)
            .castShadows(true).receiveShadows(true)
            .build(engine, entity)
        val transformManager = engine.transformManager
        transformManager.create(entity)
        transformManager.setTransform(transformManager.getInstance(entity), transform)
        scene.addEntity(entity)
        entities.add(entity)
        return entity
    }

    val box: Mesh by lazy { buildBox() }
    val rockVariants: List<Mesh> by lazy { (0 until 4).map { buildRock(it) } }
    val terrainParts: List<Mesh> by lazy { buildTerrain() }

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
        val n = 3
        val positions = ArrayList<Float>(216); val normals = ArrayList<Float>(216); val indices = ArrayList<Int>(144)
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
        val cell = 2f
        val nx = (WorldLayout.VALLEY_WIDTH/cell).toInt()+1
        val nz = (WorldLayout.VALLEY_DEPTH/cell).toInt()+1
        val x0 = -WorldLayout.VALLEY_WIDTH/2f; val z0 = -WorldLayout.VALLEY_DEPTH/2f
        val heights = FloatArray(nx*nz); val positions = FloatArray(nx*nz*3)
        for (j in 0 until nz) for (i in 0 until nx) {
            val x=x0+i*cell; val z=z0+j*cell; val h=WorldLayout.groundHeight(x,z); val v=j*nx+i
            heights[v]=h; positions[v*3]=x; positions[v*3+1]=h; positions[v*3+2]=z
        }
        val normals = FloatArray(nx*nz*3)
        fun heightAt(i: Int, j: Int): Float = heights[j.coerceIn(0,nz-1)*nx+i.coerceIn(0,nx-1)]
        for (j in 0 until nz) for (i in 0 until nx) {
            val v=j*nx+i
            val dx=(heightAt(i+1,j)-heightAt(i-1,j))/(2f*cell)
            val dz=(heightAt(i,j+1)-heightAt(i,j-1))/(2f*cell)
            val inv=1f/sqrt(dx*dx+1f+dz*dz)
            normals[v*3]=-dx*inv; normals[v*3+1]=inv; normals[v*3+2]=-dz*inv
        }
        val variantCount = Theme.GRASS.size
        val variantIndices = Array(variantCount) { ArrayList<Int>() }
        for (j in 0 until nz-1) for (i in 0 until nx-1) {
            val v00=j*nx+i; val v01=(j+1)*nx+i; val v11=v01+1; val v10=v00+1
            val variant=(WorldLayout.hash01(i,j,7.3f)*variantCount).toInt().coerceAtMost(variantCount-1)
            val idx = variantIndices[variant]
            idx.add(v00); idx.add(v01); idx.add(v11); idx.add(v00); idx.add(v11); idx.add(v10)
        }
        val vb = newVertexBuffer(positions, normals)
        return Theme.GRASS.indices.map { t ->
            registerMesh(vb, newIndexBuffer(variantIndices[t]), variantIndices[t].size,
                Box(floatArrayOf(0f,1.5f,0f), floatArrayOf(WorldLayout.VALLEY_WIDTH/2f+1f,4f,WorldLayout.VALLEY_DEPTH/2f+1f)))
        }
    }

    private fun newVertexBuffer(positions: FloatArray, normals: FloatArray): VertexBuffer {
        val vb = VertexBuffer.Builder()
            .bufferCount(1).vertexCount(positions.size/3)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 24)
            .attribute(VertexBuffer.VertexAttribute.NORMAL, 0, VertexBuffer.AttributeType.FLOAT3, 12, 24)
            .build(engine)
        val buf = ByteBuffer.allocateDirect(positions.size*8).order(ByteOrder.nativeOrder())
        val floats = buf.asFloatBuffer()
        var i = 0
        while (i < positions.size) {
            floats.put(positions[i]); floats.put(positions[i+1]); floats.put(positions[i+2])
            floats.put(normals[i]); floats.put(normals[i+1]); floats.put(normals[i+2])
            i += 3
        }
        vb.setBufferAt(engine, 0, buf)
        return vb
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
        for (entity in entities) { scene.removeEntity(entity); engine.destroyEntity(entity) }
        for (instance in materialInstances) engine.destroyMaterialInstance(instance)
        litMaterial?.let { engine.destroyMaterial(it) }
        for (mesh in meshes) { engine.destroyVertexBuffer(mesh.vertexBuffer); engine.destroyIndexBuffer(mesh.indexBuffer) }
    }
}

class CameraRig(private val engine: Engine) {
    val camera: Camera
    private val cameraEntity: Int = EntityManager.get().create()
    private var focusX = WorldLayout.SPAWN_X
    private var focusZ = WorldLayout.SPAWN_Z
    private var targetFocusX = focusX
    private var targetFocusZ = focusZ
    private var zoom = 16f
    private var targetZoom = zoom
    private var viewportWidth = 1
    private var viewportHeight = 1

    init {
        camera = engine.createCamera(cameraEntity)
        camera.setExposure(16f, 1f/125f, 100f)
    }

    fun setViewport(width: Int, height: Int) { viewportWidth = width; viewportHeight = height }

    fun panByPixels(dxPx: Float, dyPx: Float) {
        val worldPerPixel = 2f*zoom/viewportHeight
        val dx = dxPx*worldPerPixel; val dy = dyPx*worldPerPixel
        val yawRad = Math.toRadians(Theme.CAMERA_YAW_DEGREES.toDouble())
        val s = sin(yawRad).toFloat(); val c = cos(yawRad).toFloat()
        targetFocusX += dy*s - dx*c
        targetFocusZ += dy*c + dx*s
        clampFocus()
    }

    fun zoomBy(factor: Float) { targetZoom = (targetZoom*factor).coerceIn(9f, 34f) }

    fun update(dt: Float) {
        val panLerp = 1f - exp(-12f*dt)
        val zoomLerp = 1f - exp(-10f*dt)
        focusX += (targetFocusX-focusX)*panLerp
        focusZ += (targetFocusZ-focusZ)*panLerp
        zoom += (targetZoom-zoom)*zoomLerp
        apply()
    }

    private fun apply() {
        val yawRad = Math.toRadians(Theme.CAMERA_YAW_DEGREES.toDouble())
        val pitchRad = Math.toRadians(Theme.CAMERA_PITCH_DEGREES.toDouble())
        val cy = cos(yawRad).toFloat()*cos(pitchRad).toFloat()
        val sy = sin(yawRad).toFloat()*cos(pitchRad).toFloat()
        val py = sin(pitchRad).toFloat()
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
        targetFocusX = targetFocusX.coerceIn(-WorldLayout.VALLEY_WIDTH/2f+8f, WorldLayout.VALLEY_WIDTH/2f-8f)
        targetFocusZ = targetFocusZ.coerceIn(-WorldLayout.VALLEY_DEPTH/2f+8f, WorldLayout.VALLEY_DEPTH/2f-8f)
    }

    fun screenToGround(px: Float, py: Float): FloatArray {
        val s = sin(Math.toRadians(Theme.CAMERA_YAW_DEGREES.toDouble())).toFloat()
        val c = cos(Math.toRadians(Theme.CAMERA_YAW_DEGREES.toDouble())).toFloat()
        val sp = sin(Math.toRadians(Theme.CAMERA_PITCH_DEGREES.toDouble())).toFloat()
        val cp = cos(Math.toRadians(Theme.CAMERA_PITCH_DEGREES.toDouble())).toFloat()
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
            x.coerceIn(-WorldLayout.VALLEY_WIDTH/2f+1f, WorldLayout.VALLEY_WIDTH/2f-1f),
            z.coerceIn(-WorldLayout.VALLEY_DEPTH/2f+1f, WorldLayout.VALLEY_DEPTH/2f-1f),
        )
    }

    fun projectToScreen(x: Float, z: Float): FloatArray {
        val s = sin(Math.toRadians(Theme.CAMERA_YAW_DEGREES.toDouble())).toFloat()
        val c = cos(Math.toRadians(Theme.CAMERA_YAW_DEGREES.toDouble())).toFloat()
        val sp = sin(Math.toRadians(Theme.CAMERA_PITCH_DEGREES.toDouble())).toFloat()
        val cp = cos(Math.toRadians(Theme.CAMERA_PITCH_DEGREES.toDouble())).toFloat()
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
    val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val view: View = engine.createView()
    private var swapChain: SwapChain? = null
    private var world: WorldRenderer? = null
    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var lastFrameNanos = 0L

    fun bind(world: WorldRenderer) {
        this.world = world
        view.scene = world.scene
        view.camera = world.camera
    }

    fun onSurfaceAvailable(surface: Surface) {
        swapChain = engine.createSwapChain(surface)
        start()
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        view.viewport = Viewport(0, 0, width, height)
        world?.onViewport(width, height)
    }

    fun onSurfaceDestroyed() {
        stop()
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = null
    }

    fun start() {
        if (!running) { running = true; lastFrameNanos = 0L; choreographer.postFrameCallback(frameCallback) }
    }

    fun stop() { running = false; choreographer.removeFrameCallback(frameCallback) }

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos: Long ->
        val dt = if (lastFrameNanos == 0L) 0f else (frameTimeNanos-lastFrameNanos)*1e-9f
        lastFrameNanos = frameTimeNanos
        draw(dt, frameTimeNanos)
        if (running) choreographer.postFrameCallback(frameCallback)
    }

    private fun draw(dt: Float, frameTimeNanos: Long) {
        val chain = swapChain ?: return
        val w = world ?: return
        w.update(dt)
        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    fun destroy() {
        stop()
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = null
        engine.destroyView(view)
        engine.destroyRenderer(renderer)
        engine.destroy()
    }
}
