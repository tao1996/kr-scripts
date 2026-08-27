package com.projectkr.shell

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import com.google.android.material.snackbar.Snackbar
import androidx.fragment.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.omarea.common.shell.KeepShellPublic
import com.projectkr.shell.databinding.FragmentHomeBinding
import com.projectkr.shell.utils.GpuUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*


class FragmentHome : androidx.fragment.app.Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var globalSPF: SharedPreferences
    private var timer: Timer? = null
    private fun showMsg(msg: String) {
        this.view?.let { Snackbar.make(it, msg, Snackbar.LENGTH_LONG).show() }
    }

    private lateinit var spf: SharedPreferences
    private var myHandler = Handler()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.homeClearRam.setOnClickListener {
            binding.homeRaminfoText.text = getString(R.string.please_wait)
            Thread(Runnable {
                KeepShellPublic.doCmdSync("sync\n" + "echo 3 > /proc/sys/vm/drop_caches\n" + "echo 1 > /proc/sys/vm/compact_memory")
                myHandler.postDelayed({
                    try {
                        updateRamInfo()
                        Toast.makeText(context, getString(R.string.monitor_cache_cleared), Toast.LENGTH_SHORT).show()
                    } catch (ex: java.lang.Exception) {
                    }
                }, 600)
            }).start()
        }
        binding.homeClearSwap.setOnClickListener {
            binding.homeZramsizeText.text = getString(R.string.please_wait)
            Thread(Runnable {
                KeepShellPublic.doCmdSync("sync\n" + "echo 1 > /proc/sys/vm/compact_memory")
                myHandler.postDelayed({
                    try {
                        updateRamInfo()
                        Toast.makeText(context, getString(R.string.monitor_ram_cleared), Toast.LENGTH_SHORT).show()
                    } catch (ex: java.lang.Exception) {
                    }
                }, 600)
            }).start()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        if (isDetached) {
            return
        }
        stopTimer()
        timer = Timer()
        timer!!.schedule(object : TimerTask() {
            override fun run() {
                updateInfo()
            }
        }, 0, 1000)
        updateRamInfo()
    }

    private var activityManager: ActivityManager? = null

    fun format1(value: Double): String {

        var bd = BigDecimal(value)
        bd = bd.setScale(1, RoundingMode.HALF_UP)
        return bd.toString()
    }

    @SuppressLint("SetTextI18n")
    private fun updateRamInfo() {
        try {
            val info = ActivityManager.MemoryInfo()
            if (activityManager == null) {
                activityManager = context!!.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            }
            activityManager!!.getMemoryInfo(info)
            val totalMem = (info.totalMem / 1024 / 1024f).toInt()
            val availMem = (info.availMem / 1024 / 1024f).toInt()
            binding.homeRaminfoText.text = "${((totalMem - availMem) * 100 / totalMem)}% (${totalMem / 1024 + 1}GB)"
            binding.homeRaminfo.setData(totalMem.toFloat(), availMem.toFloat())
            val swapInfo = KeepShellPublic.doCmdSync("free -m | grep Swap")
            if (swapInfo.contains("Swap")) {
                try {
                    val swapInfos = swapInfo.substring(swapInfo.indexOf(" "), swapInfo.lastIndexOf(" ")).trim()
                    if (Regex("[\\d]{1,}[\\s]{1,}[\\d]{1,}").matches(swapInfos)) {
                        val total = swapInfos.substring(0, swapInfos.indexOf(" ")).trim().toInt()
                        val use = swapInfos.substring(swapInfos.indexOf(" ")).trim().toInt()
                        val free = total - use
                        binding.homeSwapstateChat.setData(total.toFloat(), free.toFloat())
                        if (total > 99) {
                            binding.homeZramsizeText.text = "${(use * 100.0 / total).toInt()}% (${format1(total / 1024.0)}GB)"
                        } else {
                            binding.homeZramsizeText.text = "${(use * 100.0 / total).toInt()}% (${total}MB)"
                        }
                    }
                } catch (ex: java.lang.Exception) {
                }
                // home_swapstate.text = swapInfo.substring(swapInfo.indexOf(" "), swapInfo.lastIndexOf(" ")).trim()
            } else {
            }
        } catch (ex: Exception) {
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateInfo() {
        val gpuFreq = GpuUtils.getGpuFreq() + "Mhz"
        val gpuLoad = GpuUtils.getGpuLoad()
        myHandler.post {
            try {
                binding.homeGpuFreq.text = gpuFreq
                binding.homeGpuLoad.text = String.format(getString(R.string.monitor_laod), gpuLoad)
                if (gpuLoad > -1) {
                    binding.homeGpuChat.setData(100.toFloat(), (100 - gpuLoad).toFloat())
                }
            } catch (ex: Exception) {
                Log.e("Exception", ex.message ?: "")
            }
        }
    }

    private fun stopTimer() {
        if (this.timer != null) {
            timer!!.cancel()
            timer = null
        }
    }

    override fun onPause() {
        stopTimer()
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
