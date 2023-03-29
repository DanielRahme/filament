/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.filament.suzanne

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Bundle
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import android.view.animation.LinearInterpolator
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.*
import java.nio.ByteBuffer
import java.nio.channels.Channels
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : Activity() {
    // Make sure to initialize the correct Filament JNI layer.
    companion object {
        init {
            Filament.init()
            Utils.init()
        }
    }

    // The View we want to render into
    // UiHelper is provided by Filament to manage SurfaceView and SurfaceTexture
    // DisplayHelper is provided by Filament to manage the display
    // Choreographer is used to schedule new frames

    // Engine creates and destroys Filament resources
    // Each engine must be accessed from a single thread of your choosing
    // Resources cannot be shared across engines
    // A renderer instance is tied to a single surface (SurfaceView, TextureView, etc.)
    // A scene holds all the renderable, lights, etc. to be drawn
    // A view defines a viewport, a scene and a camera for rendering
    // Should be pretty obvious :)
    // Filament entity representing a renderable object
    // A swap chain is Filament's representation of a surface
    // Performs the rendering and schedules new frames

    private lateinit var surfaceView: SurfaceView
    private lateinit var uiHelper: UiHelper
    private lateinit var displayHelper: DisplayHelper
    private lateinit var choreographer: Choreographer

    private lateinit var engine: Engine
    private lateinit var scene: Scene
    private lateinit var renderer: Renderer
    private lateinit var renderTarget: RenderTarget
    private lateinit var view: View
    private lateinit var camera: Camera
    private lateinit var material: Material
    private lateinit var materialInstance: MaterialInstance
    private lateinit var baseColor: Texture
    private lateinit var normal: Texture
    private lateinit var ao: Texture
    private lateinit var roughness: Texture
    private lateinit var metallic: Texture
    private lateinit var offscreenColorTexture: Texture
    private lateinit var mesh: Mesh
    private lateinit var ibl: Ibl
    @Entity private var light = 0
    private var swapChain: SwapChain? = null
    private val frameScheduler = FrameCallback()
    private val animator = ValueAnimator.ofFloat(0.0f, (2.0 * PI).toFloat())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup SurfaceView
        surfaceView = SurfaceView(this)
        setContentView(surfaceView)
        choreographer = Choreographer.getInstance()
        displayHelper = DisplayHelper(this)

        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = SurfaceCallback()
        // NOTE: To choose a specific rendering resolution, add the following line:
        // uiHelper.setDesiredSize(1280, 720)
        uiHelper.attachTo(surfaceView)

        // Setup Filament
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())

        // Setup View
        //
        // Tell the view which camera we want to use
        // Tell the view which scene we want to render
        // Enable dynamic resolution with a default target frame rate of 60fps

        // NOTE: Try to disable post-processing (tone-mapping, etc.) to see the difference
        //
        // view.isPostProcessingEnabled = false

        view.camera = camera
        view.scene = scene
        val options = View.DynamicResolutionOptions()
        options.enabled = true
        view.dynamicResolutionOptions = options

        val vp = view.getViewport()
        view.viewport = Viewport(0, 0, vp.width, vp.height)

        // Setup Scene
        // Load Material
        readUncompressedAsset("materials/common_pbr.filamat").let {
            material = Material.Builder().payload(it, it.remaining()).build(engine)
        }

        // Setup Material
        //
        // Create an instance of the material to set different parameters on it
        // NOTE: that the textures are stored in drawable-nodpi to prevent the system
        // from automatically resizing them based on the display's density
        materialInstance = material.createInstance()
        baseColor =
                loadTexture(engine, resources, R.drawable.dirty_gold_01_color, TextureType.COLOR)
        normal = loadTexture(engine, resources, R.drawable.dirty_gold_01_normal, TextureType.NORMAL)
        ao = loadTexture(engine, resources, R.drawable.dirty_gold_01_ao, TextureType.DATA)
        roughness =
                loadTexture(engine, resources, R.drawable.dirty_gold_01_roughness, TextureType.DATA)
        metallic =
                loadTexture(engine, resources, R.drawable.dirty_gold_01_metallic, TextureType.DATA)

        // A texture sampler does not need to be kept around or destroyed
        val sampler = TextureSampler()
        sampler.anisotropy = 8.0f
        materialInstance.setParameter("baseColor", baseColor, sampler)
        materialInstance.setParameter("normal", normal, sampler)
        materialInstance.setParameter("ao", ao, sampler)
        materialInstance.setParameter("roughness", roughness, sampler)
        materialInstance.setParameter("metallic", metallic, sampler)

        // Load image based lighting
        ibl = loadIbl(assets, "envs/flower_road_no_sun_2k", engine)
        ibl.indirectLight.intensity = 40_000.0f

        scene.skybox = ibl.skybox
        scene.indirectLight = ibl.indirectLight

        // This map can contain named materials that will map to the material names
        // loaded from the filamesh file. The material called "DefaultMaterial" is
        // applied when no named material can be found
        // Load the mesh in the filamesh format (see filamesh tool)
        val materials = mapOf("DefaultMaterial" to materialInstance)
        mesh = loadMesh(assets, "models/monkey.filamesh", materials, engine)

        // Move the mesh down
        // Filament uses column-major matrices
        /* ktlint-disable */
        engine.transformManager.setTransform(
                engine.transformManager.getInstance(mesh.renderable),
                floatArrayOf(
                        1.0f,
                        0.0f,
                        0.0f,
                        0.0f,
                        0.0f,
                        1.0f,
                        0.0f,
                        0.0f,
                        0.0f,
                        0.0f,
                        1.0f,
                        0.0f,
                        0.0f,
                        -1.2f,
                        0.0f,
                        1.0f
                )
        )
        /* ktlint-enable */

        offscreenColorTexture =
                Texture.Builder()
                        .width(vp.width)
                        .height(vp.height)
                        .levels(1)
                        .usage(Texture.Usage.COLOR_ATTACHMENT + Texture.Usage.SAMPLEABLE)
                        .format(Texture.InternalFormat.RGBA8)
                        .build(engine)

        renderTarget =
                RenderTarget.Builder()
                        .texture(RenderTarget.AttachmentPoint.COLOR, offscreenColorTexture)
                        .build(engine)

        view.setRenderTarget(renderTarget)
        // Add the entity to the scene to render it
        scene.addEntity(mesh.renderable)

        // We now need a light, let's create a directional light
        light = EntityManager.get().create()

        // Create a color from a temperature (D65)
        val (r, g, b) = Colors.cct(6_500.0f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(r, g, b)
                .intensity(110_000.0f) // Intensity of the sun in lux on a clear day
                .direction(-0.753f, -1.0f, 0.890f) // The direction is normalized on our behalf
                .castShadows(true)
                .build(engine, light)

        // Set the exposure on the camera, this exposure follows the sunny f/16 rule
        // Since we've defined a light that has the same intensity as the sun, it
        // guarantees a proper exposure
        scene.addEntity(light)
        camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f)

        // Start Animation
        // Animate the triangle
        animator.interpolator = LinearInterpolator()
        animator.duration = 18_000
        animator.repeatMode = ValueAnimator.RESTART
        animator.repeatCount = ValueAnimator.INFINITE
        animator.addUpdateListener { a ->
            val v = (a.animatedValue as Float)
            camera.lookAt(cos(v) * 4.5, 1.5, sin(v) * 4.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        }
        animator.start()
    }

    private fun loadImageBasedLight() {}

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameScheduler)
        animator.start()
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameScheduler)
        animator.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop the animation and any pending frame
        // Always detach the surface before destroying the engine
        choreographer.removeFrameCallback(frameScheduler)
        animator.cancel()
        uiHelper.detach()

        // Cleanup all resources
        destroyMesh(engine, mesh)
        destroyIbl(engine, ibl)
        engine.destroyTexture(baseColor)
        engine.destroyTexture(normal)
        engine.destroyTexture(ao)
        engine.destroyTexture(roughness)
        engine.destroyTexture(metallic)
        engine.destroyEntity(light)
        engine.destroyRenderer(renderer)
        engine.destroyRenderTarget(renderTarget)
        engine.destroyMaterialInstance(materialInstance)
        engine.destroyMaterial(material)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)

        // Engine.destroyEntity() destroys Filament related resources only
        // (components), not the entity itself
        val entityManager = EntityManager.get()
        entityManager.destroy(light)
        entityManager.destroy(camera.entity)

        // Destroying the engine will free up any resource you may have forgotten
        // to destroy, but it's recommended to do the cleanup properly
        engine.destroy()
    }

    inner class FrameCallback : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            // Schedule the next frame
            choreographer.postFrameCallback(this)

            // This check guarantees that we have a swap chain
            if (uiHelper.isReadyToRender) {
                // If beginFrame() returns false you should skip the frame
                // This means you are sending frames too quickly to the GPU
                if (renderer.beginFrame(swapChain!!, frameTimeNanos)) {
                    renderer.render(view)
                    renderer.endFrame()
                }
            }
        }
    }

    inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface)
            displayHelper.attach(renderer, surfaceView.display)
        }

        override fun onDetachedFromSurface() {

            displayHelper.detach()
            // Required to ensure we don't return before Filament is done executing the
            // destroySwapChain command, otherwise Android might destroy the Surface too early
            swapChain?.let {
                engine.destroySwapChain(it)
                engine.flushAndWait()
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            val aspect = width.toDouble() / height.toDouble()
            camera.setProjection(45.0, aspect, 0.1, 20.0, Camera.Fov.VERTICAL)

            view.viewport = Viewport(0, 0, width, height)
        }
    }

    private fun readUncompressedAsset(assetName: String): ByteBuffer {
        assets.openFd(assetName).use { fd ->
            val input = fd.createInputStream()
            val dst = ByteBuffer.allocate(fd.length.toInt())

            val src = Channels.newChannel(input)
            src.read(dst)
            src.close()

            return dst.apply { rewind() }
        }
    }
}
