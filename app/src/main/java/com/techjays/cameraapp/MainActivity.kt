package com.techjays.cameraapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.techjays.inappcamera.InAppCameraActivity
import android.util.Log



class MainActivity : AppCompatActivity() {
    lateinit var click: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        click = findViewById(R.id.click)

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
            }
        }
    }
}