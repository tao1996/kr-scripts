package com.omarea.krscript.model

import android.content.Intent
import android.view.View
import com.omarea.krscript.ui.ParamsFileChooserRender

interface KrScriptActionHandler {
    fun openFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean
    fun onSubPageClick(pageNode: PageNode)
    fun onActionCompleted(runnableNode: RunnableNode)
    fun addToFavorites(clickableNode: ClickableNode, addToFavoritesHandler: AddToFavoritesHandler)
    fun openParamsPage(actionNode: ActionNode, view: View, onCompleted: Runnable): Boolean {
        return false
    }

    // 在交互式终端中打开并执行命令
    fun openTerminal(command: String, title: String?) {
    }

    interface AddToFavoritesHandler {
        fun onAddToFavorites(clickableNode: ClickableNode, intent: Intent?)
    }
}
