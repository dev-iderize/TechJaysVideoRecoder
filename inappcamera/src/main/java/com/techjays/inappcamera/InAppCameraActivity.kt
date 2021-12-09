package com.techjays.inappcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.techjays.inappcamera.databinding.ActivityInAppCameraBinding
import java.io.File
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

class InAppCameraActivity : AppCompatActivity(), ImageAnalysis.Analyzer, CameraXConfig.Provider {
    lateinit var mContentViewBinding: ActivityInAppCameraBinding

    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null

    var previewView: PreviewView? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture? = null
    private var isRecord: Boolean = false
    private var vidFilePath = ""
    private var handler = Handler(Looper.getMainLooper())
    private var isBack: Boolean = true
    private lateinit var cameraSelector: CameraSelector
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_app_camera)
        mContentViewBinding =
            DataBindingUtil.setContentView(
                this,
                R.layout.activity_in_app_camera
            ) as ActivityInAppCameraBinding
        init()
    }

    @SuppressLint("RestrictedApi")
    fun init() {
        isRecord = true
        isBack = true
        previewView = mContentViewBinding.previewView
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture!!.addListener(Runnable {
            try {
                val cameraProvider = cameraProviderFuture!!.get()
                startCameraX(cameraProvider, CameraSelector.LENS_FACING_BACK)
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, getExecutor())

        mContentViewBinding.bRecord.setOnClickListener {
            if (isRecord) {
                isRecord = false
                mContentViewBinding.bRecord.setColorFilter(
                    ContextCompat.getColor(
                        this,
                        R.color.red
                    ), android.graphics.PorterDuff.Mode.SRC_IN
                );
                recordVideo()
                startProgress()
                handler.postDelayed({
                    isRecord = true
                    videoCapture!!.stopRecording()
                    mContentViewBinding.countTimeProgressView.cancelCountTimeAnimation()
                    mContentViewBinding.bRecord.setColorFilter(
                        ContextCompat.getColor(
                            this,
                            R.color.white
                        ), android.graphics.PorterDuff.Mode.SRC_IN
                    );
                }, 45000)
            } else {
                handler.removeCallbacksAndMessages(null);
                isRecord = true
                videoCapture!!.stopRecording()
                mContentViewBinding.countTimeProgressView.cancelCountTimeAnimation()
                mContentViewBinding.bRecord.setColorFilter(
                    ContextCompat.getColor(
                        this,
                        R.color.white
                    ), android.graphics.PorterDuff.Mode.SRC_IN
                );
            }
        }

        mContentViewBinding.flip.setOnClickListener {
            lensFacing =
                if (lensFacing === CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            try {
                val cameraProvider = cameraProviderFuture!!.get()
                // Only bind use cases if we can query a camera with this orientation
                startCameraX(cameraProvider, lensFacing)
            } catch (e: CameraInfoUnavailableException) {
                // Do nothing
            }
        }
        /* mContentViewBinding.bCapture.setOnClickListener {
             capturePhoto()
         }*/
    }

    private fun startProgress() {
        mContentViewBinding.countTimeProgressView.startCountTimeAnimation()
    }

    @SuppressLint("RestrictedApi")
    private fun recordVideo() {
        if (videoCapture != null) {
            val movieDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .toString()
            )
            if (!movieDir.exists()) movieDir.mkdir()
            val date = Date()
            val timestamp = date.time.toString()
            vidFilePath = movieDir.absolutePath + "/" + timestamp + ".mp4"
            val vidFile = File(vidFilePath)
            try {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                videoCapture!!.startRecording(
                    VideoCapture.OutputFileOptions.Builder(vidFile).build(),
                    getExecutor()!!,
                    object : VideoCapture.OnVideoSavedCallback {
                        override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                            var intent = Intent()
                            intent.putExtra("path", vidFilePath)
                            setResult(RESULT_OK, intent)
                            finish()
                        }

                        override fun onError(
                            videoCaptureError: Int,
                            message: String,
                            cause: Throwable?
                        ) {
                            Toast.makeText(
                                this@InAppCameraActivity,
                                "Error saving video: $message",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun capturePhoto() {
        val photoDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .toString()
        )

        if (!photoDir.exists()) photoDir.mkdir()

        val date = Date()
        val timestamp = date.time.toString()
        val photoFilePath = photoDir.absolutePath + "/" + timestamp + ".jpg"
        val photoFile = File(photoFilePath)

        imageCapture!!.takePicture(
            ImageCapture.OutputFileOptions.Builder(photoFile).build(),
            getExecutor()!!,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(
                        this@InAppCameraActivity,
                        "Photo has been saved successfully.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@InAppCameraActivity,
                        "Error saving photo: " + exception.message,
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d("camera", exception.message.toString())
                }
            }
        )
    }

    fun getExecutor(): Executor? {
        return ContextCompat.getMainExecutor(this)
    }

    @SuppressLint("RestrictedApi")
    private fun startCameraX(cameraProvider: ProcessCameraProvider, lens: Int) {
        cameraProvider.unbindAll()
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lens)
            .build()
        val preview = Preview.Builder()
            .build()
        preview.setSurfaceProvider(previewView!!.surfaceProvider)

        // Image capture use case
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        // Video capture use case
        videoCapture = VideoCapture.Builder()
            .setVideoFrameRate(30)
            .build()

        // Image analysis use case
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(getExecutor()!!, this)

        //bind to lifecycle:
        cameraProvider.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector,
            preview,
            videoCapture,
        )
    }

    override fun analyze(image: ImageProxy) {
        // image processing here for the current frame
        Log.d("TAG", "analyze: got the frame at: " + image.imageInfo.timestamp)
        image.close()
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig();
    }
}