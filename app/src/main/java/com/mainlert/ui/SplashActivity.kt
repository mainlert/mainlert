package com.mainlert.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mainlert.R
import com.mainlert.utils.AdminInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Splash screen activity that displays the app logo with smooth zoom animation
 * and transitions to the main activity.
 */
class SplashActivity : AppCompatActivity() {
    private val splashTotalDuration: Long = 3000 // 3 seconds total
    private val zoomInDuration: Long = 1000 // 1 second
    private val holdDuration: Long = 500 // 0.5 second
    private val zoomOutDuration: Long = 1000 // 1 second

    private lateinit var ivLogo: ImageView
    private lateinit var tvAppName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Initialize views
        ivLogo = findViewById(R.id.iv_logo)
        tvAppName = findViewById(R.id.tv_app_name)

        // Initialize admin user in background
        initializeAdminUser()

        // Start smooth logo animation sequence
        startLogoAnimation()
    }

    /**
     * Starts the smooth logo animation sequence:
     * 1. Zoom in (1000ms)
     * 2. Hold (500ms)
     * 3. Zoom out (1000ms)
     * 4. Transition to MainActivity
     */
    private fun startLogoAnimation() {
        // Load zoom in animation
        val zoomInAnimation = AnimationUtils.loadAnimation(this, R.anim.zoom_in)

        // Set animation listener for zoom in completion
        zoomInAnimation.setAnimationListener(
            object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {
                    // Animation started
                }

                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    // Zoom in completed, hold for 500ms then zoom out
                    Handler(Looper.getMainLooper()).postDelayed({
                        startZoomOutAnimation()
                    }, holdDuration)
                }

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {
                    // Not used
                }
            },
        )

        // Start zoom in animation
        ivLogo.startAnimation(zoomInAnimation)
    }

    /**
     * Starts the zoom out animation and transitions to MainActivity
     */
    private fun startZoomOutAnimation() {
        // Load zoom out animation
        val zoomOutAnimation = AnimationUtils.loadAnimation(this, R.anim.zoom_out)

        // Set animation listener for zoom out completion
        zoomOutAnimation.setAnimationListener(
            object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {
                    // Animation started
                }

                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    // Zoom out completed, transition to main activity
                    Handler(Looper.getMainLooper()).postDelayed({
                        val intent = Intent(this@SplashActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }, 100) // Small delay for smooth transition
                }

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {
                    // Not used
                }
            },
        )

        // Start zoom out animation
        ivLogo.startAnimation(zoomOutAnimation)
    }

    /**
     * Initializes the admin user "Gnerdy" in the background.
     * This ensures the admin user is created only once during app setup.
     */
    private fun initializeAdminUser() {
        val adminInitializer = AdminInitializer(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = adminInitializer.initializeAdmin()
                if (success) {
                    android.util.Log.i("SplashActivity", "Admin user initialization completed successfully")
                } else {
                    android.util.Log.w("SplashActivity", "Admin user initialization failed or was skipped")
                }
            } catch (e: Exception) {
                android.util.Log.e("SplashActivity", "Error during admin initialization: ${e.message}", e)
            }
        }
    }
}
