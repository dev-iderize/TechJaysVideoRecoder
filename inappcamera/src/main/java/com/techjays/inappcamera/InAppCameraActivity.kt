package com.techjays.inappcamera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
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
import com.anilokcun.uwmediapicker.UwMediaPicker
import com.anilokcun.uwmediapicker.model.UwMediaPickerMediaModel
import com.daasuu.mp4compose.FillMode
import com.daasuu.mp4compose.Rotation
import com.daasuu.mp4compose.composer.Mp4Composer
import com.google.common.util.concurrent.ListenableFuture
import com.simform.videooperations.*
import com.techjays.inappcamera.databinding.ActivityInAppCameraBinding
import com.yashovardhan99.timeit.Stopwatch
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor


class InAppCameraActivity : AppCompatActivity(), ImageAnalysis.Analyzer, CameraXConfig.Provider {
    lateinit var mContentViewBinding: ActivityInAppCameraBinding
    private var mVideoUri: Uri? = null
    var ACTION_TAKE_VIDEO = 99
    private val uris = mutableListOf<Uri>()
    lateinit var activity:Activity
    private var progressDialog: Dialog? = null
    private var mPermission =
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )

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
    private lateinit var stopwatch: Stopwatch
    private var isLimit:Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_app_camera)
        mContentViewBinding =
            DataBindingUtil.setContentView(
                this,
                R.layout.activity_in_app_camera
            ) as ActivityInAppCameraBinding
        PermissionCheckers().askAllPermissions(this, mPermission)
        val bundle = intent.extras
        isLimit = bundle!!.getBoolean("video_limit")
        init()
    }

    @SuppressLint("RestrictedApi")
    fun init() {
        activity = this
        stopwatch = Stopwatch()
        stopwatch.setTextView(mContentViewBinding.stopwatchText)
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
                mContentViewBinding.flip.visibility = View.GONE
                mContentViewBinding.bRecord.setColorFilter(
                    ContextCompat.getColor(
                        this,
                        R.color.red
                    ), android.graphics.PorterDuff.Mode.SRC_IN
                );
                recordVideo()
                stopwatch.start()
                if (isLimit){
                    startProgress()
                    handler.postDelayed({
                        isRecord = true
                        videoCapture!!.stopRecording()
                        mContentViewBinding.countTimeProgressView.cancelCountTimeAnimation()
                        stopwatch.stop()
                        mContentViewBinding.bRecord.setColorFilter(
                            ContextCompat.getColor(
                                this,
                                R.color.white
                            ), android.graphics.PorterDuff.Mode.SRC_IN
                        );
                    }, 45500)
                }

            } else {
                handler.removeCallbacksAndMessages(null);
                isRecord = true
                videoCapture!!.stopRecording()
                stopwatch.stop()
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

        mContentViewBinding.add.setOnClickListener {
            if (PermissionCheckers().checkAllPermission(this, mPermission))
                UwMediaPicker
                    .with(this)                    // Activity or Fragment
                    .setGalleryMode(UwMediaPicker.GalleryMode.VideoGallery) // GalleryMode: ImageGallery/VideoGallery/ImageAndVideoGallery, default is ImageGallery
                    .setGridColumnCount(4)                                  // Grid column count, default is 3
                    .setMaxSelectableMediaCount(1)                         // Maximum selectable media count, default is null which means infinite
                    .setLightStatusBar(true)                           // Is llight status bar enable, default is true
                    .enableImageCompression(true)                // Is image compression enable, default is false
                    .setCompressionMaxWidth(1280F)                // Compressed image's max width px, default is 1280
                    .setCompressionMaxHeight(720F) // Compressed image's max height px, default is 720
                    .setCompressFormat(Bitmap.CompressFormat.JPEG)        // Compressed image's format, default is JPEG
                    .setCompressionQuality(85)                // Image compression quality, default is 85
                    .setCompressedFileDestinationPath(
                        "${
                            this.getExternalFilesDir(null)!!.path
                        }/Pictures"
                    )    // Compressed image file's destination path, default is "${application.getExternalFilesDir(null).path}/Pictures"
                    .setCancelCallback { }                    // Will be called when user cancels media selection
                    .launch(::onMediaSelected)
        }

        mContentViewBinding.back.setOnClickListener {
            finish()
        }
    }

    private fun startProgress() {
        mContentViewBinding.countTimeProgressView.startCountTimeAnimation()
    }

    private fun onMediaSelected(selectedMediaList: List<UwMediaPickerMediaModel>?) {
        if (selectedMediaList != null) {
            /* val selectedMediaPathList = selectedMediaList.map { it.mediaPath }
             var intent = Intent()
             intent.putExtra("path", selectedMediaList[0].mediaPath)
             setResult(RESULT_OK, intent)
             finish()*/
            com(selectedMediaList[0].mediaPath)
        } else {
            Toast.makeText(this, "Unexpected Error", Toast.LENGTH_SHORT).show()
        }
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
                           /* var intent = Intent()
                            intent.putExtra("path", vidFilePath)
                            setResult(RESULT_OK, intent)
                            finish()*/
                            com(vidFilePath)
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
                    Log.d("ithu",exception.message.toString())
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ACTION_TAKE_VIDEO && resultCode == RESULT_OK) {
            val videoUri: Uri = data?.data!!
            val path = GetPath.getPath(this, videoUri)
            if (path != null) {
                var intent = Intent()
                intent.putExtra("path", path)
                setResult(RESULT_OK, intent)
                finish()
            }
        }
    }

    fun com(videoPath:String){
        showProgressDialog(this,false)
        val outputPath = Common.getFilePath(this, Common.VIDEO)
        val ffmpegQueryExtension = FFmpegQueryExtension()
        val uri = Uri.fromFile(File(videoPath))

        try {
            val file = File(videoPath)
            val size = file.length()
            Log.d("SizeOfVideo",size.toString())
            if (size > 10000000) {
                val query = ffmpegQueryExtension.compressor(uri.toString(), 1080, 1350, outputPath)
                CallBackOfQuery().callQuery(query, object : FFmpegCallBack {
                    override fun process(logMessage: LogMessage) {
                        Log.d("process on", logMessage.toString())
                    }

                    @SuppressLint("SetTextI18n")
                    override fun success() {
                        Log.d("ffg video out", outputPath)
                        val output = Common.getFilePath(this@InAppCameraActivity, Common.VIDEO)

                        Mp4Composer(outputPath, output)
                            .rotation(Rotation.NORMAL)
                            .size(1080, 1350)
                            .fillMode(FillMode.PRESERVE_ASPECT_FIT)
                            .listener(object : Mp4Composer.Listener {
                                override fun onProgress(progress: Double) {

                                }

                                override fun onCurrentWrittenVideoTime(timeUs: Long) {

                                }

                                override fun onCompleted() {
                                    /*  val uri:Uri = Uri.fromFile(File(output))*/
                                    hideProgressDialog()
                                    val intent = Intent()
                                    intent.putExtra("path", outputPath)
                                    setResult(RESULT_OK, intent)
                                    finish()
                                }

                                override fun onCanceled() {

                                }

                                override fun onFailed(exception: java.lang.Exception) {

                                }
                            })
                            .start()
                    }

                    override fun cancel() {

                    }

                    override fun failed() {

                    }
                })
            } else{
                hideProgressDialog()
                val intent = Intent()
                intent.putExtra("path", videoPath)
                setResult(RESULT_OK, intent)
                finish()
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

  /*  fun getVideoFilePath(): String? {
        val movieDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .toString()
        )
        if (!movieDir.exists()) movieDir.mkdir()
        val date = Date()
        val timestamp = date.time.toString()
        vidFilePath = movieDir.absolutePath + "/" + timestamp + ".mp4"
        return vidFilePath
    }
*/
    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    fun showProgressDialog(context: Context, isCancelable: Boolean) {
        hideProgressDialog()
        progressDialog = Dialog(context)
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.dialog_progress_custom, null)
        progressDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        progressDialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        progressDialog!!.setContentView(view)
        progressDialog!!.setCancelable(isCancelable)
        progressDialog!!.show()
    }

    fun hideProgressDialog() {
        try {
            if (progressDialog != null && progressDialog!!.isShowing) {
                progressDialog!!.dismiss()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}