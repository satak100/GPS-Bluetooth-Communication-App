package com.gpsbluetoothapp

import android.os.Bundle
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.gpsbluetoothapp.databinding.ActivityLogViewerBinding

class LogViewerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLogViewerBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        loadLogMessages()
    }
    
    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Log Messages"
        
        // Setup buttons
        binding.btnClearAllLogs.setOnClickListener {
            clearLogs()
        }
        
        binding.btnScrollToBottom.setOnClickListener {
            scrollToBottom()
        }
        
        binding.btnScrollToTop.setOnClickListener {
            scrollToTop()
        }
        
        binding.btnRefreshLogs.setOnClickListener {
            loadLogMessages()
        }
    }
    
    private fun loadLogMessages() {
        val logMessages = intent.getStringExtra("log_messages") ?: "No log messages available"
        binding.tvAllLogs.text = logMessages
        
        // Update line count
        val lineCount = logMessages.lines().filter { it.isNotBlank() }.size
        binding.tvLogCount.text = "Lines: $lineCount"
        
        // Auto-scroll to bottom after loading
        binding.tvAllLogs.post {
            scrollToBottom()
        }
    }
    
    private fun clearLogs() {
        binding.tvAllLogs.text = "Logs cleared..."
        // Send result back to main activity to clear its logs too
        setResult(RESULT_OK)
    }
    
    private fun scrollToBottom() {
        binding.scrollViewAllLogs.fullScroll(ScrollView.FOCUS_DOWN)
    }
    
    private fun scrollToTop() {
        binding.scrollViewAllLogs.fullScroll(ScrollView.FOCUS_UP)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
