package com.thorito.jitsi_meet

import android.app.Activity
import android.content.Intent
import android.content.BroadcastReceiver
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import org.jitsi.meet.sdk.*
import java.net.URL
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.os.CountDownTimer
import android.content.IntentFilter

/** JitsiMeetPlugin
example: https://github.com/jitsi/jitsi-meet-sdk-samples/blob/18c35f7625b38233579ff34f761f4c126ba7e03a/android/kotlin/JitsiSDKTest/app/src/main/kotlin/net/jitsi/sdktest/MainActivity.kt
 */
public class JitsiMeetPlugin() : FlutterPlugin, MethodCallHandler,
    ActivityAware {
    private lateinit var methodChannel: MethodChannel
    private lateinit var pointersChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private val eventStreamHandler = JitsiMeetEventStreamHandler.instance
    private var activity: Activity? = null

    constructor(activity: Activity) : this() {
        this.activity = activity
    }
    /**
     * FlutterPlugin interface implementations
     */
    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel =
            MethodChannel(flutterPluginBinding.binaryMessenger, "jitsi_meet")
        methodChannel.setMethodCallHandler(this)
        pointersChannel = MethodChannel(flutterPluginBinding.binaryMessenger, POINTERS_CHANNEL)
        pointersChannel.setMethodCallHandler(this)
        eventChannel = EventChannel(
            flutterPluginBinding.binaryMessenger,
            "jitsi_meet_events"
        )
        eventChannel.setStreamHandler(JitsiMeetEventStreamHandler.instance)
        val timer = object: CountDownTimer(20000000, 2000) {
            override fun onTick(millisUntilFinished: Long) {
                if (update) {
                    val userData = mapOf("x" to x, "y" to y, "width" to screenWidth, "height" to screenHeight)
                    pointersChannel.invokeMethod("receiveUserPointer", userData, object: MethodChannel.Result{
                        override fun success(result: Any?) {
                            update = false
                        }

                        override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                            update = false
                        }

                        override fun notImplemented() {
                            update = false
                        }
                    })
                }
            }

            override fun onFinish() {}
        }
        timer.start()
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        pointersChannel.setMethodCallHandler(null)
    }

    override fun onDetachedFromActivity() {
        this.activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    companion object {

        var x = 0.0
        var y = 0.0
        var screenWidth = 0
        var screenHeight = 0
        var update = false

        const val JITSI_MEETING_CLOSE = "JITSI_MEETING_CLOSE"
        const val POINTERS_CHANNEL = "samples.flutter.dev/pointer"
    }

    /**
     * MethodCallHandler interface implementations
     */
    override fun onMethodCall(
        @NonNull call: MethodCall,
        @NonNull result: Result
    ) {

        when (call.method) {
            "joinMeeting" -> joinMeeting(call, result)
            "setAudioMuted" -> setAudioMuted(call, result)
            "handUp" -> hangUp(call, result)
            "sendUserPointer" -> {
                sendPointer(call, result)
            }
            "enablePointerButton" -> {
                enablePointerButton(call, result)
            }
            "closeMeeting" -> closeMeeting(call, result)
            else -> result.notImplemented()
        }
    }

    private fun sendPointer(call: MethodCall, result: Result) {
        val userInfo = arrayListOf(
            call.argument<String>("name"),
            call.argument<Double>("x").toString(),
            call.argument<Double>("y").toString(),
            call.argument<String>("id").toString()
        )
        activity?.sendBroadcast(Intent(DrawerActivity.POINTERS_CLICKED_TAG).putExtra(DrawerActivity.DRAW_POINTER, userInfo))
    }

    private fun enablePointerButton(call: MethodCall, result: Result) {
        activity?.sendBroadcast(Intent(DrawerActivity.POINTERS_BUTTON_FLAG).putExtra(DrawerActivity.POINTER_ENABLE_VALUE, call.argument<Boolean>("enable")))
    }

    /**
     * Method call to join a meeting
     */
    private fun joinMeeting(call: MethodCall, result: Result) {
        val room = call.argument<String>("room")
        if (room.isNullOrBlank()) {
            result.error(
                "400",
                "room can not be null or empty",
                "room can not be null or empty"
            )
            return
        }

        val serverUrlString: String =
            call.argument("serverURL") ?: "https://meet.jit.si"

        val subject: String? = call.argument("subject")
        val token: String? = call.argument("token")
        val isAudioMuted: Boolean? = call.argument("audioMuted")
        val isAudioOnly: Boolean? = call.argument("audioOnly")
        val isVideoMuted: Boolean? = call.argument("videoMuted")

        val displayName: String? = call.argument("userDisplayName")
        val email: String? = call.argument("userEmail")
        val userAvatarUrlString: String? = call.argument("userAvatarUrl")
        val userInfo = JitsiMeetUserInfo().apply {
            if (displayName != null) this.displayName = displayName
            if (email != null) this.email = email
            if (userAvatarUrlString != null) avatar = URL(userAvatarUrlString)
        }

        val serverURL = URL(serverUrlString)

        val options = JitsiMeetConferenceOptions.Builder().run {
            setRoom(room)
            if (serverURL != null) setServerURL(serverURL)
            if (subject != null) setSubject(subject)
            if (token != null) setToken(token)
            if (isAudioMuted != null) setAudioMuted(isAudioMuted)
            if (isAudioOnly != null) setAudioOnly(isAudioOnly)
            if (isVideoMuted != null) setVideoMuted(isVideoMuted)
            if (displayName != null || email != null || userAvatarUrlString != null) {
                setUserInfo(userInfo)
            }

            val featureFlags =
                call.argument<HashMap<String, Any?>>("featureFlags")
            featureFlags?.forEach { (key, value) ->
                // Can only be bool, int or string according to
                // the overloads of setFeatureFlag.
                when (value) {
                    is Boolean -> setFeatureFlag(key, value)
                    is Int -> setFeatureFlag(key, value)
                    else -> setFeatureFlag(key, value.toString())
                }
            }

            val configOverrides =
                call.argument<HashMap<String, Any?>>("configOverrides")
            configOverrides?.forEach { (key, value) ->
                // Can only be bool, int, array of strings or string according to
                // the overloads of setConfigOverride.
                when (value) {
                    is Boolean -> setConfigOverride(key, value)
                    is Int -> setConfigOverride(key, value)
                    is Array<*> -> setConfigOverride(
                        key,
                        value as Array<out String>
                    )
                    else -> setConfigOverride(key, value.toString())
                }
            }

            build()
        }

        JitsiMeetPluginActivity.launchActivity(activity!!, options)
        result.success("Successfully joined room: $room")
    }

    private fun closeMeeting(call: MethodCall, result: Result) {
        val intent = Intent("JITSI_MEETING_CLOSE")
        activity?.sendBroadcast(intent)
        result.success(null)
    }

    private fun setAudioMuted(call: MethodCall, result: Result) {
        val isMuted = call.argument<Boolean>("isMuted") ?: false

        val muteBroadcastIntent: Intent =
            BroadcastIntentHelper.buildSetAudioMutedIntent(isMuted)
        LocalBroadcastManager.getInstance(activity!!.applicationContext)
            .sendBroadcast(muteBroadcastIntent)

        result.success("Successfully set audio muted to: $isMuted")
    }

    private fun hangUp(call: MethodCall, result: Result) {
        val hangUpIntent: Intent = BroadcastIntentHelper.buildHangUpIntent()
        LocalBroadcastManager.getInstance(activity!!.applicationContext)
            .sendBroadcast(hangUpIntent)
        closeMeeting(call, result);
        result.success("Successfully hung up.")
    }
}
