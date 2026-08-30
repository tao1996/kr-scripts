package com.projectkr.shell

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import com.omarea.common.ui.ProgressBarDialog
import com.projectkr.shell.databinding.ActivityFileSelectorBinding
import com.projectkr.shell.ui.AdapterFileSelector
import java.io.File

class ActivityFileSelector : AppCompatActivity() {
    companion object {
        val MODE_FILE = 0
        val MODE_FOLDER = 1
    }

    private lateinit var binding: ActivityFileSelectorBinding
    private var adapterFileSelector: AdapterFileSelector? = null
    var extension = ""
    var mode = MODE_FILE
    var path = ""
    var multiple = false
    var separator = "\n"

    override fun onCreate(savedInstanceState: Bundle?) {
        // TODO:ThemeSwitch.switchTheme(this)

        super.onCreate(savedInstanceState)
        binding = ActivityFileSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        // setTitle(R.string.app_name)

        // 显示返回按钮
        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { _ ->
            finish()
        }

        intent.extras?.run {
            if (containsKey("extension") == true) {
                extension = "" + getString("extension")
                if (!extension.startsWith(".")) {
                    extension = ".$extension"
                }
                if (extension.isNotEmpty()) {
                    title = title.toString() + "($extension)"
                }
            }
            if (containsKey("mode") == true) {
                mode = getInt("mode")
                if (mode == MODE_FOLDER) {
                    title = getString(R.string.title_activity_folder_selector)
                }
            }
            if (containsKey("path") == true) {
                path = "" + getString("path")
            }
            if (containsKey("multiple") == true) {
                multiple = getBoolean("multiple")
            }
            if (containsKey("separator") == true) {
                val sep = getString("separator")
                if (!sep.isNullOrEmpty()) {
                    separator = sep
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && adapterFileSelector != null && adapterFileSelector!!.goParent()) {
            return true
        } else {
            setResult(Activity.RESULT_CANCELED, Intent())
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        var grant = true
        for (result in grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                grant = false;
            }
        }

        if (requestCode == 111) {
            if (grant == false) {
                Toast.makeText(applicationContext, "没有读取文件的权限！", Toast.LENGTH_LONG).show()
            } else {
                loadData()
            }
        }
    }

    private fun checkPermission(permission: String): Boolean = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 111);
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        if (checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) && checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            val sdcard = File(Environment.getExternalStorageDirectory().absolutePath)
            val startDir = if (path.isNotEmpty()) File(path) else sdcard
            val dir = if (startDir.exists() && startDir.isDirectory) startDir else sdcard

            if (dir.exists() && dir.isDirectory) {
                val list = dir.listFiles()
                if (list == null) {
                    Toast.makeText(applicationContext, "获取文件列表失败！", Toast.LENGTH_LONG).show()
                    return
                }
                val onSelected =  Runnable {
                    val file: File? = adapterFileSelector!!.selectedFile
                    if (file != null) {
                        this.setResult(Activity.RESULT_OK, Intent().putExtra("file", file.absolutePath))
                        this.finish()
                    }
                }
                adapterFileSelector = if (mode == MODE_FOLDER) {
                    AdapterFileSelector.FolderChooser(dir, onSelected, ProgressBarDialog(this))
                } else {
                    AdapterFileSelector.FileChooser(dir, onSelected, ProgressBarDialog(this), extension, multiple, separator)
                }

                binding.fileSelectorList.adapter = adapterFileSelector

                if (multiple && mode == MODE_FILE) {
                    binding.multiSelectBar.visibility = View.VISIBLE
                    binding.btnMultiCancel.setOnClickListener {
                        setResult(Activity.RESULT_CANCELED, Intent())
                        finish()
                    }
                    binding.btnMultiConfirm.setOnClickListener {
                        val result = adapterFileSelector!!.selectedFilesResult
                        if (result.isNullOrEmpty()) {
                            Toast.makeText(applicationContext, "请先选择文件！", Toast.LENGTH_SHORT).show()
                        } else {
                            this.setResult(Activity.RESULT_OK, Intent().putExtra("file", result))
                            this.finish()
                        }
                    }
                } else {
                    binding.multiSelectBar.visibility = View.GONE
                }
            }
        } else {
            requestPermissions()
        }
    }
}
