package com.projectkr.shell

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.io.File

class AdapterProject(private val context: Context, private val projects: List<File>) : BaseAdapter() {

    override fun getCount(): Int = projects.size

    override fun getItem(position: Int): File = projects[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder
        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_project, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val file = getItem(position)
        holder.name.text = file.name
        holder.path.text = file.absolutePath
        return view
    }

    private class ViewHolder(view: View) {
        val name: TextView = view.findViewById(R.id.project_name)
        val path: TextView = view.findViewById(R.id.project_path)
    }
}