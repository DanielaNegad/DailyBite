package com.example.dailybite

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.dailybite.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1) לחבר את ה-Toolbar כ-ActionBar לפני NavigationUI
        setSupportActionBar(binding.toolbar)

        // 2) לאחזר את ה-NavController לפי ה-ID המדויק ב-XML: nav_host
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment
        navController = navHost.navController

        // 3) יעדי על (ללא חץ חזור) — עדכני לפי היעדים הראשיים שלך
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.feedFragment,
                R.id.newPostFragment,
                R.id.profileFragment
            )
        )

        // 4) לחבר ActionBar ל-Navigation
        setupActionBarWithNavController(navController, appBarConfiguration)

        // 5) לחבר BottomNavigation ל-Navigation
        binding.bottomNav.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
