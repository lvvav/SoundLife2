package app.soundlife

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File

/**
 * Видеокружок: фронтальная камера, запись пока держишь кнопку (не дольше 60 с).
 * onFinished получает готовый файл mp4 или null (отмена или ошибка).
 */
@Composable
internal fun CircleRecorder(onFinished: (File?) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember { PreviewView(ctx) }
    var capture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var rec by remember { mutableStateOf<Recording?>(null) }
    val file = remember { File(ctx.cacheDir, "circle-rec.mp4") }
    val cancelled = remember { BooleanArray(1) }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            val provider = future.get()
            val p = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.SD)).build()
            val vc = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, p, vc)
            capture = vc
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            cancelled[0] = true
            runCatching { rec?.stop() }
            runCatching { future.get().unbindAll() }
        }
    }

    fun stop() {
        rec?.stop()
        rec = null
    }

    fun start() {
        val vc = capture ?: return
        if (rec != null) return
        if (file.exists()) file.delete()
        var pending = vc.output.prepareRecording(ctx, FileOutputOptions.Builder(file).build())
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        rec = pending.start(ContextCompat.getMainExecutor(ctx)) { event ->
            if (event is VideoRecordEvent.Finalize && !cancelled[0]) {
                onFinished(if (event.hasError()) null else file)
            }
        }
    }

    // Не дольше 60 секунд, как на сайте
    LaunchedEffect(rec) {
        if (rec != null) {
            delay(60_000)
            stop()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(260.dp).clip(CircleShape).background(Color.DarkGray)) {
            AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(if (rec != null) Color.Red else Color.White)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        start()
                        tryAwaitRelease()
                        stop()
                    })
                },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (rec != null) "Идёт запись. Отпустите, чтобы отправить" else "Удерживайте кнопку, чтобы записать кружок",
            color = Color.White, fontSize = 14.sp,
        )
        TextButton(onClick = {
            if (rec != null) {
                cancelled[0] = true
                stop()
            }
            onFinished(null)
        }) { Text("Отмена", color = Color.White) }
    }
}
