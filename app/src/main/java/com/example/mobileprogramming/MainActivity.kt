package com.example.mobileprogramming

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.mobileprogramming.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. ViewBinding Initialization
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Setup Bottom Navigation listener
        setupNavigationListener()

        // 3. Load default fragment (HomeDashboard) on initial launch
        if (savedInstanceState == null) {
            replaceFragment(HomeDashboardFragment())
        }
    }

    /**
     * Replaces the current fragment inside the container
     */
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    /**
     * Navigates to a specific tab from inside fragments (e.g. going to MyPage on limit exceed)
     */
    fun navigateToTab(tabId: Int) {
        binding.bottomNavigation.selectedItemId = tabId
    }

    private fun setupNavigationListener() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is HomeFragment) {
                currentFragment.showExitConfirmationDialog {
                    // Temporarily detach listener to avoid recursive triggers
                    binding.bottomNavigation.setOnItemSelectedListener(null)
                    binding.bottomNavigation.selectedItemId = item.itemId
                    setupNavigationListener() // reattach
                    
                    val fragment = when (item.itemId) {
                        R.id.action_home -> HomeDashboardFragment()
                        R.id.action_storage -> StorageFragment()
                        R.id.action_mypage -> MyPageFragment()
                        else -> HomeDashboardFragment()
                    }
                    replaceFragment(fragment)
                }
                false // intercept tab change synchronously
            } else {
                val fragment = when (item.itemId) {
                    R.id.action_home -> HomeDashboardFragment()
                    R.id.action_storage -> StorageFragment()
                    R.id.action_mypage -> MyPageFragment()
                    else -> HomeDashboardFragment()
                }
                replaceFragment(fragment)
                true
            }
        }
    }
}