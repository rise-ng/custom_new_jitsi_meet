package com.thorito.jitsi_meet

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import com.thorito.jitsi_meet.R
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.embedding.android.FlutterActivity
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.content.SharedPreferences
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class DrawerActivity : AppCompatActivity() {

    private var enableTouch = false
    private var pointersBtn: Button? = null
    private lateinit var pointersMap: HashMap<String, List<String?>>
    private var screenWidth = 0
    private var screenHeight = 0

    private val pointerReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val updateFlags = intent.getBooleanExtra(UPDATE_FLAGS, false)
            val drawPointer = intent.getSerializableExtra(DRAW_POINTER) as ArrayList<String>?
            if (updateFlags) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                triggerTouch()
                pointersBtn?.visibility = View.VISIBLE
            }
            if (drawPointer?.isEmpty()?.not() == true) {
                drawAtPosition(
                        drawPointer[NAME_INDEX],
                        drawPointer[X_COORDINATION_INDEX].toDouble(),
                        drawPointer[Y_COORDINATION_INDEX].toDouble(),
                        drawPointer[ID_INDEX].toString()
                )
            }
        }
    }

    private val pointerEnabled: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val updateFlags = intent.getBooleanExtra(POINTER_ENABLE_VALUE, false)
            if (!updateFlags) {
                pointersBtn?.visibility = View.GONE
            }
        }
    }

    private val closeCallReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val returnIntent = Intent()
            returnIntent.putExtra(BACK_BUTTON_TAG, true)
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawer)

        pointersMap = HashMap()

        getScreenDimensions()
        disableTouch()
        hideSystemUI()

        pointersBtn = findViewById(R.id.btnPointers)
        pointersBtn?.setOnClickListener {
            disableTouch()
            triggerTouch()
        }
    }

    private fun triggerTouch() {
        enableTouch = enableTouch.not()
        if (enableTouch.not()) {
            if (pointersMap.keys.contains("0")) {
                JitsiMeetPlugin.update = true
                JitsiMeetPlugin.x = 4000.0
                JitsiMeetPlugin.y = 4000.0
                JitsiMeetPlugin.screenWidth = screenWidth
                JitsiMeetPlugin.screenHeight = screenHeight
                drawAtPosition("Me", 4000.0, 4000.0, "0")
            }
        }
    }

    private fun disableTouch() {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        pointersBtn?.visibility = View.GONE
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window,
                window.decorView.findViewById(android.R.id.content)).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.toDouble()
        val y = event.y.toDouble()
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (enableTouch) {
                    JitsiMeetPlugin.update = true
                    JitsiMeetPlugin.x = x
                    JitsiMeetPlugin.y = y
                    JitsiMeetPlugin.screenWidth = screenWidth
                    JitsiMeetPlugin.screenHeight = screenHeight

                    drawAtPosition("Me", x, y, "0")
                }
            }
        }

        return false
    }

    private fun getScreenDimensions() {
        val size = Point()
        val windowManager = windowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            windowManager.defaultDisplay.getSize(size)
            screenWidth = size.x
            screenHeight = size.y
        } else {
            val display: Display = windowManager.defaultDisplay
            screenWidth = display.width
            screenHeight = display.height
        }
    }

    private fun drawAtPosition(userName: String, x: Double, y: Double, id: String) {
        val layout = findViewById<RelativeLayout>(R.id.drawerLayout)

        if (pointersMap.keys.contains(id)) {
            val userIndex: Int? = pointersMap[id]?.get(3)?.toInt()
            userIndex?.let {
                layout.removeViewAt(userIndex!!)
                updateIndexes(userIndex)
            }
        }

        val group = LinearLayout(applicationContext)
        val groupLayoutParameters = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        var positionX = x.toInt()
        var positionY = y.toInt()
        groupLayoutParameters.setMargins(positionX - 30, positionY - 50, 0, 0)
        group.orientation = LinearLayout.VERTICAL
        group.layoutParams = groupLayoutParameters

        val pointer = ImageView(applicationContext)
        val pointerLayoutParameters = LinearLayout.LayoutParams(100, 100)
        pointer.layoutParams = pointerLayoutParameters
        pointer.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.clicker, null))
        group.addView(pointer)

        val name = TextView(applicationContext)
        name.text = "    $userName"
        group.addView(name)

        val index = getNextIndex()
        layout.addView(group, index)

        pointersMap[id] = listOf(userName, x.toString(), y.toString(), index.toString())
    }

    private fun getNextIndex(): Int {
        val layout = findViewById<RelativeLayout>(R.id.drawerLayout)
        var index = 0
        while (true) {
            if (layout.getChildAt(index) == null) return index
            index++
        }
    }

    private fun updateIndexes(index: Int) {
        for ((key, value) in pointersMap) {
            if (value [3] != null && value[3]!!.toInt() > index) {
                val newList = mutableListOf<String>()
                newList.add(value[NAME_INDEX]!!)
                newList.add(value[X_COORDINATION_INDEX]!!)
                newList.add(value[Y_COORDINATION_INDEX]!!)
                newList.add((value[POSITION_INDEX]!!.toInt() - 1).toString())
                pointersMap[key] = newList
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(pointerReceiver, IntentFilter(POINTERS_CLICKED_TAG))
        registerReceiver(closeCallReceiver, IntentFilter(JitsiMeetPlugin.JITSI_MEETING_CLOSE))
        registerReceiver(pointerEnabled, IntentFilter(POINTERS_BUTTON_FLAG))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pointerReceiver)
        unregisterReceiver(closeCallReceiver)
        unregisterReceiver(pointerEnabled)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
            val returnIntent = Intent()
            returnIntent.putExtra(BACK_BUTTON_TAG, true)
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val BACK_BUTTON_TAG = "back"
        const val POINTERS_CLICKED_TAG = "pointers"
        const val UPDATE_FLAGS = "updateFlags"
        const val DRAW_POINTER = "drawPointer"
        const val NAME_INDEX = 0
        const val X_COORDINATION_INDEX = 1
        const val Y_COORDINATION_INDEX = 2
        const val POSITION_INDEX = 3
        const val ID_INDEX = 3
        const val POINTERS_CHANNEL = "samples.flutter.dev/pointer"
        const val USER_DATA = "userData"
        const val SEND_USER_DATA = "sendUserData"
        const val POINTERS_BUTTON_FLAG = "pointers_flag"
        const val POINTER_ENABLE_VALUE = "button_enabled"
    }
}
