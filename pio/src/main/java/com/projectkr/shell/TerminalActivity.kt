package com.projectkr.shell

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.omarea.common.shell.KeepShellPublic
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalRenderer
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File
import java.lang.ref.WeakReference


class TerminalActivity : AppCompatActivity(), TerminalViewClient {
    private var termView: TerminalView? = null
    private var termSession: TerminalSession? = null
    private var commandToRun: String? = null
    private var extraEnv: Array<String>? = null
    private var commandSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        val command = intent.getStringExtra("command") ?: ""
        extraEnv = intent.getStringArrayExtra("env")
        val title = intent.getStringExtra("title")
        if (!title.isNullOrEmpty()) {
            setTitle(title)
        }

        commandToRun = if (command.isNotEmpty()) command else null

        val container = findViewById<FrameLayout>(R.id.terminal_container)

        val view = TerminalView(this, null)
        view.mRenderer = TerminalRenderer(40, Typeface.MONOSPACE)
        view.setTerminalViewClient(this)
        view.setBackgroundColor(Color.BLACK)

        termView = view
        termSession = getTerminalSession()
        view.attachSession(termSession!!)

        container.addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                termSession?.finishIfRunning()
                finish()
            }
        })
    }

    private fun getTerminalSession(): TerminalSession {
        val cwd = filesDir.absolutePath
        val rooted = try {
            KeepShellPublic.checkRoot()
        } catch (ex: Exception) {
            false
        }

        val shell = if (rooted) resolveRootShell() else resolveShell()

        val baseEnv = arrayOf(
                "PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/system_ext/bin:/vendor/bin:/vendor/xbin:/odm/bin",
                "HOME=$cwd",
                "TERM=xterm-256color"
        )
        val env = if (extraEnv == null) baseEnv else (baseEnv + extraEnv!!)

        return TerminalSession(
                shell,
                cwd,
                arrayOf(),
                env,
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                getTermSessionClient()
        )
    }

    private fun resolveRootShell(): String {
        val candidates = arrayOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/su/bin/su",
                "/system_ext/bin/su",
                "/vendor/bin/su",
                "/data/adb/magisk/su"
        )
        for (candidate in candidates) {
            if (File(candidate).exists()) {
                return candidate
            }
        }
        // 交由 execvp 通过 PATH 查找
        return "su"
    }

    private fun resolveShell(): String {
        return if (File("/system/bin/sh").exists()) "/system/bin/sh" else "/bin/sh"
    }

    private fun writeCommand() {
        val session = termSession ?: return
        commandToRun?.let { cmd ->
            // 先关闭回显，稍等片刻确保 stty 已生效，再注入命令，
            // 这样执行器命令就不会被打印到屏幕。
            session.write("stty -echo\r")
            Handler(Looper.getMainLooper()).postDelayed({
                if (termSession === session && session.isRunning) {
                    session.write(cmd + "\r")
                    session.write("stty echo\r")
                }
            }, 150)
        }
    }

    private fun getTermSessionClient(): TerminalSessionClient {
        val weakActivityReference = WeakReference(this)
        return object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                runOnUiThread {
                    weakActivityReference.get()?.termView?.onScreenUpdated()
                }
            }

            override fun onTitleChanged(updatedSession: TerminalSession) { }

            override fun onSessionFinished(finishedSession: TerminalSession) {
                runOnUiThread {
                    weakActivityReference.get()?.let { activity ->
                        activity.termView?.mTermSession?.finishIfRunning()
                        activity.finish()
                    }
                }
            }

            override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
                if (text != null) {
                    val cm = weakActivityReference.get()
                            ?.getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
                }
            }

            override fun onPasteTextFromClipboard(session: TerminalSession?) {
                runOnUiThread {
                    val activity = weakActivityReference.get() ?: return@runOnUiThread
                    val cm = activity.getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val clip = cm?.primaryClip?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.coerceToText(activity)?.toString()
                    if (!clip.isNullOrEmpty() && activity.termView?.mEmulator != null) {
                        activity.termView?.mEmulator?.paste(clip)
                    }
                }
            }

            override fun onBell(session: TerminalSession) { }

            override fun onColorsChanged(changedSession: TerminalSession) { }

            override fun onTerminalCursorStateChange(state: Boolean) { }

            override fun getTerminalCursorStyle(): Int {
                return TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE
            }

            override fun logError(tag: String?, message: String?) {
                if (message != null) Log.e(tag, message)
            }

            override fun logWarn(tag: String?, message: String?) {
                if (message != null) Log.w(tag, message)
            }

            override fun logInfo(tag: String?, message: String?) {
                if (message != null) Log.i(tag, message)
            }

            override fun logDebug(tag: String?, message: String?) {
                if (message != null) Log.d(tag, message)
            }

            override fun logVerbose(tag: String?, message: String?) {
                if (message != null) Log.v(tag, message)
            }

            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
                Log.e(tag, message + "\n" + Log.getStackTraceString(e))
            }

            override fun logStackTrace(tag: String?, e: Exception?) {
                Log.e(tag, Log.getStackTraceString(e))
            }
        }
    }

    // ============ TerminalViewClient ============
    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent?) {
        if (termSession?.isRunning == true) {
            val view = termView ?: return
            view.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) { }

    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            termSession?.finishIfRunning()
            finish()
            return true
        }
        return false
    }

    override fun onLongPress(event: MotionEvent?): Boolean = false

    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false

    override fun onEmulatorSet() {
        if (!commandSent) {
            commandSent = true
            writeCommand()
        }
    }

    // ============ 日志 ============
    override fun logError(tag: String?, message: String?) {
        if (message != null) Log.e(tag, message)
    }

    override fun logWarn(tag: String?, message: String?) {
        if (message != null) Log.w(tag, message)
    }

    override fun logInfo(tag: String?, message: String?) {
        if (message != null) Log.i(tag, message)
    }

    override fun logDebug(tag: String?, message: String?) {
        if (message != null) Log.d(tag, message)
    }

    override fun logVerbose(tag: String?, message: String?) {
        if (message != null) Log.v(tag, message)
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag, message + "\n" + Log.getStackTraceString(e))
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag, Log.getStackTraceString(e))
    }

    override fun onDestroy() {
        termSession?.finishIfRunning()
        termView = null
        termSession = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TerminalActivity"
    }
}