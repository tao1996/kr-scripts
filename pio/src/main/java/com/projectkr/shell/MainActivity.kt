package com.projectkr.shell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.AdapterView
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.omarea.krscript.model.PageNode
import java.io.File

class MainActivity : AppCompatActivity() {

    private val projects = ArrayList<File>()
    private lateinit var projectList: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectList = findViewById(R.id.project_list)
        projectList.emptyView = findViewById(R.id.project_empty)
        projectList.adapter = AdapterProject(this, projects)
        projectList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            openProject(projects[position])
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        } else {
            loadProjects()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadProjects()
            } else {
                Toast.makeText(this, R.string.kr_write_external_storage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadProjects() {
        projects.clear()
        val dnaDir = File(Environment.getExternalStorageDirectory(), "DNA")
        if (dnaDir.isDirectory) {
            dnaDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
                if (file.isDirectory && file.name.startsWith("DNA_")) {
                    projects.add(file)
                }
            }
        }
        (projectList.adapter as AdapterProject).notifyDataSetChanged()
    }

    private fun openProject(dir: File) {
        val xml = findEntryXml(dir)
        if (xml == null) {
            Toast.makeText(this, "项目目录中未找到 XML 配置", Toast.LENGTH_SHORT).show()
            return
        }

        val page = PageNode(xml.absolutePath)
        page.pageConfigPath = xml.absolutePath
        page.title = dir.name

        val intent = Intent(this, ActionPage::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("page", page)
        startActivity(intent)
    }

    private fun findEntryXml(dir: File): File? {
        val candidates = listOf(
            File(dir, "config.xml"),
            File(dir, dir.name + ".xml"),
            File(dir, "main.xml")
        )
        candidates.forEach { if (it.isFile) return it }

        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".xml", ignoreCase = true) }
            ?.minByOrNull { it.name }
    }
}