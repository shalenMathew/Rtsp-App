package com.example.vyorius

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.vyorius.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

class MainActivity : AppCompatActivity() {

    /*  SAVING VIDEO IN GALLERY  BRANCH */

    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var recordPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var binding: ActivityMainBinding
    private lateinit var currentRecordingFile: File
    private lateinit var progressBar: ProgressBar
    private lateinit var popOut: Button
    var isRecording = false
    var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        askPermission()

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )


        videoLayout = binding.videoLayout
        val playBtn = binding.playButton
        val abortBtn = binding.AbortBtn
        val recordBtn = binding.RecordButton
        progressBar = binding.progressBar
        popOut = binding.PipButton

        lifecycleScope.launch(Dispatchers.Default) {

            val args = arrayListOf("-vvv")
            val vlc= LibVLC(this@MainActivity,args)
            val mediaPlayer = MediaPlayer(vlc)
            val recordPlayer = MediaPlayer(vlc)

            withContext(Dispatchers.Main) {
                libVLC =vlc
                this@MainActivity.mediaPlayer = mediaPlayer
                this@MainActivity.recordPlayer = recordPlayer

                setUpListeners()
                setUpBtnClicks(playBtn,abortBtn,recordBtn,popOut)

            }

        }

    }

    private fun askPermission() {


        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P){

            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),100)

            }

        }

    }

    private fun setUpBtnClicks(
        playBtn: Button,
        abortBtn: Button,
        recordBtn: MaterialButton,
        popOut: Button
    ) {

        playBtn.setOnClickListener {

            val rtspUrl = binding.rtspUrlInput.text.toString()

            if(rtspUrl.isBlank() || rtspUrl.isEmpty()){
                isPlaying = false
                Toast.makeText(this,"Enter RTSP URL",Toast.LENGTH_SHORT).show()
            }else{
                progressBar.visibility = View.VISIBLE
                playRTSP(rtspUrl)
            }
        }

        abortBtn.setOnClickListener {

            if (isRecording){
                Toast.makeText(this,"Stop the recording to stop the stream.",Toast.LENGTH_SHORT).show()
            }else{
                mediaPlayer.stop()
                mediaPlayer.detachViews()
            }

        }

        recordBtn.setOnClickListener {

            isRecording = !isRecording

            if(isPlaying  && isRecording){

                progressBar.visibility = View.VISIBLE

                recordBtn.icon =  AppCompatResources. getDrawable(this ,R.drawable.recording)
                recordBtn.text = "Recording..."
                recordBtn.setBackgroundColor(resources.getColor(R.color.red))
                recordBtn.iconTint =ContextCompat.getColorStateList(this, R.color.white)

                Toast.makeText(this,"Recording Stream ...",Toast.LENGTH_SHORT).show()

                recordStream()

            }else if (!isPlaying){
                Toast.makeText(this,"Play the stream to start recording....",Toast.LENGTH_SHORT).show()
            }
            else{

                // this cond runs when isRecord - false && isPlaying - true

                recordBtn.text = "Record"
                recordBtn.setBackgroundColor(resources.getColor(R.color.purple))
                recordBtn.icon =  null

                Toast.makeText(this,"Recording is being saved. DON'T QUIT THE APP!!!",Toast.LENGTH_SHORT).show()

                recordPlayer.stop()
                recordPlayer.detachViews()

                if(::currentRecordingFile.isInitialized){
                    savedVideoToGallery(currentRecordingFile)
                }else{
                    Toast.makeText(this,"The media player is been intialized... please click record only " +
                            "once stream starts",Toast.LENGTH_SHORT).show()
                }


            }

        }

        popOut.setOnClickListener {

            if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.O){

                val pip = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16,9))
                    .build()

                enterPictureInPictureMode(pip)
            }

        }
    }

    private fun savedVideoToGallery(file: File) {

        val filename = file.name

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            lifecycleScope.launch(Dispatchers.IO) {

                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Vyorius") // For Android 10+
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val resolver = contentResolver
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values)

                uri?.let {

                    try {

                        resolver.openOutputStream(it)?.use { outStream ->
                            file.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }

                        values.clear()
                        values.put(MediaStore.Video.Media.IS_PENDING, 0)
                        resolver.update(it, values, null, null)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Saved to gallery!!!", Toast.LENGTH_SHORT).show()
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Failed to save video: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }

                } ?:
                run {

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity,"Failed to save video to gallery.",Toast.LENGTH_SHORT).show()
                    }
                }
            }

        }else{

            lifecycleScope.launch(Dispatchers.IO) {

                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val outputDir = File(moviesDir, "Vyorius")
                if (!outputDir.exists()) outputDir.mkdirs()

                val newFile = File(outputDir, filename)

                try {
                    file.copyTo(newFile, overwrite = true)

                    // Notify MediaScanner so it appears in Gallery
                    MediaScannerConnection.scanFile(
                        this@MainActivity,
                        arrayOf(newFile.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Saved to gallery (legacy)", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Legacy save failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }

                }

            }
        }

    }

    private fun recordStream() {

        val rtspUrl = binding.rtspUrlInput.text.toString()

        lifecycleScope.launch(Dispatchers.IO) {

            val filename = "recorded_stream_${System.currentTimeMillis()}.mp4"

            currentRecordingFile = File(getExternalFilesDir(null), filename)

            if( rtspUrl.isNotBlank()){

                val media = Media(libVLC,rtspUrl.toUri())
                val options = arrayListOf(
                    ":sout=#file{dst=${currentRecordingFile.absolutePath}}",
                    ":sout-keep"
                )
                media.setHWDecoderEnabled(true,false)
                options.forEach {
                    media.addOption(it)
                }

                withContext(Dispatchers.Main) {
                    recordPlayer.media = media
                    recordPlayer.play()
                }
            }
        }

    }

    fun playRTSP(url:String){

        if(mediaPlayer.isPlaying){
            mediaPlayer.stop()
        }

        mediaPlayer.detachViews()

        if(!videoLayout.isAttachedToWindow){
            Toast.makeText(this,"Video layout not attached to window.",Toast.LENGTH_SHORT).show()
            return
        }

        mediaPlayer.attachViews(videoLayout,null,false,false)

        val media = Media(libVLC, url.toUri())
        media.setHWDecoderEnabled(true,false)
        media.addOption(":network-caching=500")
        mediaPlayer.media = media
        mediaPlayer.play()

    }

    fun setUpListeners(){

        mediaPlayer.setEventListener({ event ->

            when(event.type){

                MediaPlayer.Event.EncounteredError->{
                    isPlaying = false
                    Toast.makeText(this,"Stream encountered an error.",Toast.LENGTH_SHORT).show()
                    Log.e("TAG", "Stream encountered an error.")
                    progressBar.visibility = View.GONE
                }

                MediaPlayer.Event.EndReached -> {
                    isPlaying = false
                    Toast.makeText(this,"Stream ended.",Toast.LENGTH_SHORT).show()
                    Log.d("TAG", "Stream ended.")
                    progressBar.visibility = View.GONE
                }
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    Toast.makeText(this,"Stream started playing successfully.",Toast.LENGTH_SHORT).show()
                    Log.d("TAG", "Stream started playing.")
                    progressBar.visibility = View.GONE
                }

                MediaPlayer.Event.Stopped -> {
                    isPlaying = false
                    Toast.makeText(this,"Stream stopped.",Toast.LENGTH_SHORT).show()
                    Log.d("TAG", "Stream stopped successfully.")
                    progressBar.visibility = View.GONE
                }

            }
        })

        recordPlayer.setEventListener({ event ->
            when(event.type){

                MediaPlayer.Event.EncounteredError->{
                    Toast.makeText(this,"Recordings encountered an error.",Toast.LENGTH_SHORT).show()
                    Log.e("TAG", "Recording encountered an error.")
                    progressBar.visibility = View.GONE
                }

                MediaPlayer.Event.EndReached -> {
                    Toast.makeText(this,"Recordings ended.",Toast.LENGTH_SHORT).show()
                    Log.d("TAG", "Recordings ended.")
                    progressBar.visibility = View.GONE
                }
                MediaPlayer.Event.Playing -> {
                    Toast.makeText(this,"Recordings started playing successfully.",Toast.LENGTH_SHORT).show()
                    Log.d("TAG", "Recordings started playing.")
                    progressBar.visibility = View.GONE
                }

                MediaPlayer.Event.Stopped -> {
                    Toast.makeText(this,"Recordings stopped.",Toast.LENGTH_SHORT).show()
                    Log.d("TAG", "Recordings stopped successfully.")
                    progressBar.visibility = View.GONE
                }

            }
        })

    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        mediaPlayer.detachViews()
        recordPlayer.release()
        libVLC.release()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode){

            binding.rtspUrlInput.visibility = View.GONE
            binding.playButton.visibility = View.GONE
            binding.PipButton.visibility = View.GONE
            binding.RecordButton.visibility = View.GONE
            binding.AbortBtn.visibility = View.GONE


        }else{

            binding.rtspUrlInput.visibility = View.VISIBLE
            binding.playButton.visibility = View.VISIBLE
            binding.PipButton.visibility = View.VISIBLE
            binding.RecordButton.visibility = View.VISIBLE
            binding.AbortBtn.visibility = View.VISIBLE

        }

    }

}