package com.techjays.cameraapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.techjays.inappcamera.InAppCameraActivity


class MainActivity : AppCompatActivity() {
    lateinit var click: TextView
    lateinit var videoView: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        click = findViewById(R.id.click)
        videoView = findViewById(R.id.videoset)

        click.setOnClickListener {
            val intent = Intent(this, InAppCameraActivity::class.java)
            intent.putExtra("video_limit", true)
            startActivityForResult(intent, 5)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 5)
        {
            if (resultCode === RESULT_OK) { // Activity.RESULT_OK
                Log.d("check",data!!.getStringExtra("path")!!)
                var vidpath = data!!.getStringExtra("path")!!
                videoView.setVideoURI(vidpath.toUri())
                videoView.start()

            }
        }
    }
}