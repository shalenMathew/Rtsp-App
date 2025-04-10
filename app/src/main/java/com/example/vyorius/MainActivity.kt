package com.example.vyorius

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.example.vyorius.databinding.ActivityMainBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import org.videolan.libvlc.interfaces.IMedia.Type.File
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var recordPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var binding: ActivityMainBinding
    var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoLayout = binding.videoLayout
        val playBtn = binding.playButton
        val abortBtn = binding.AbortBtn
        val recordBtn = binding.RecordButton


        val args = arrayListOf("-vvv")
        libVLC = LibVLC(this,args)
        mediaPlayer = MediaPlayer(libVLC)
        recordPlayer = MediaPlayer(libVLC)

        mediaPlayer.setEventListener({ event ->

            when(event.type){

                MediaPlayer.Event.EncounteredError->{
                    Toast.makeText(this,"Stream encountered an error.",Toast.LENGTH_SHORT).show()
                    Log.e("TAG", "Stream encountered an error.")
                }

                MediaPlayer.Event.EndReached -> {
                    Toast.makeText(this,"Stream ended.",Toast.LENGTH_SHORT).show()
                    Log.d("TAG", "Stream ended.")
                }
                MediaPlayer.Event.Playing -> {
                    Toast.makeText(this,"Stream started playing successfully.",Toast.LENGTH_SHORT).show()
                    Log.d("TAG", "Stream started playing.")
                }

                MediaPlayer.Event.Stopped -> {
                    Toast.makeText(this,"Stream stopped.",Toast.LENGTH_SHORT).show()
                    Log.d("TAG", "Stream stopped successfully.")
                }

            }
        })

        recordPlayer.setEventListener({ event ->
            when(event.type){

                MediaPlayer.Event.EncounteredError->{
                    Toast.makeText(this,"Recordings encountered an error.",Toast.LENGTH_SHORT).show()
                    Log.e("TAG", "Recording encountered an error.")
                }

                MediaPlayer.Event.EndReached -> {
                    Toast.makeText(this,"Recordings ended.",Toast.LENGTH_SHORT).show()
                    Log.d("TAG", "Recordings ended.")
                }
                MediaPlayer.Event.Playing -> {
                    Toast.makeText(this,"Recordings started playing successfully.",Toast.LENGTH_SHORT).show()
                    Log.d("TAG", "Recordings started playing.")
                }

                MediaPlayer.Event.Stopped -> {
                    Toast.makeText(this,"Recordings stopped.",Toast.LENGTH_SHORT).show()
                    Log.d("TAG", "Recordings stopped successfully.")
                }

            }
        })

        setUpBtnClicks(playBtn,abortBtn,recordBtn)

    }

    private fun setUpBtnClicks(playBtn: Button, abortBtn: Button, recordBtn: MaterialButton) {
        playBtn.setOnClickListener {

            val rtspUrl = binding.rtspUrlInput.text.toString()

            if(rtspUrl.isBlank() || rtspUrl.isEmpty()){
                Toast.makeText(this,"Enter RTSP URL",Toast.LENGTH_SHORT).show()
            }else{
                playRTSP(rtspUrl)
            }
        }

        abortBtn.setOnClickListener {
            mediaPlayer.stop()
            mediaPlayer.detachViews()
        }

        recordBtn.setOnClickListener {

            isRecording = !isRecording

            if(isRecording){

                recordBtn.icon =  AppCompatResources. getDrawable(this ,R.drawable.recording)
                recordBtn.text = "Recording..."
                recordBtn.setBackgroundColor(resources.getColor(R.color.red))
                recordBtn.iconTint =ContextCompat.getColorStateList(this, R.color.white)

                Toast.makeText(this,"Recording Stream ...",Toast.LENGTH_SHORT).show()

                recordStream()

            }
            else{
                recordBtn.text = "Record"
                recordBtn.setBackgroundColor(resources.getColor(R.color.purple))
                recordBtn.icon =  null

                Toast.makeText(this,"Recording stopped.",Toast.LENGTH_SHORT).show()

                recordPlayer.stop()
                recordPlayer.detachViews()
            }

        }
    }

    private fun recordStream() {

        val filename = "recorded_stream_${System.currentTimeMillis()}.mp4"
        val outputFile = File(getExternalFilesDir(null), filename)
        val rtspUrl = binding.rtspUrlInput.text.toString()

        if(rtspUrl.isNotEmpty() || rtspUrl.isNotBlank()){

            val media = Media(libVLC,rtspUrl.toUri())
            val options = arrayListOf(
                ":sout=#file{dst=${outputFile.absolutePath}}",
                ":sout-keep"
            )
            media.setHWDecoderEnabled(true,false)
            options.forEach {
                media.addOption(it)
            }

            recordPlayer.media = media
            recordPlayer.play()

        }

    }

    fun playRTSP(url:String){

        if(mediaPlayer.isPlaying){
            mediaPlayer.stop()
        }

        mediaPlayer.detachViews()
        mediaPlayer.attachViews(videoLayout,null,false,false)

        val media = Media(libVLC, url.toUri())
        media.setHWDecoderEnabled(true,false)
        media.addOption(":network-caching=150")
        mediaPlayer.media = media
        mediaPlayer.play()

    }


    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        mediaPlayer.detachViews()
        recordPlayer.release()
        libVLC.release()
    }

}