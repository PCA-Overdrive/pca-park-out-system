package com.example.pca_app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_BT_PERMISSION = 1001

        // 라즈베리파이 Bluetooth MAC 주소
        private const val TARGET_DEVICE_NAME = ""
        private const val TARGET_DEVICE_MAC = "88:A2:9E:47:F3:BB"

        // Bluetooth Classic SPP UUID
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private lateinit var loginLayout: LinearLayout
    private lateinit var controlLayout: LinearLayout

    private lateinit var tvBtStatusLogin: TextView
    private lateinit var tvBtStatusMain: TextView
    private lateinit var tvLastPacket: TextView

    private lateinit var etUserId: EditText
    private lateinit var etPassword: EditText

    private lateinit var btnLeftExit: View
    private lateinit var btnRightExit: View
    private lateinit var btnStraightExit: View
    private lateinit var btnCancelExit: Button

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    @Volatile
    private var isConnected = false

    @Volatile
    private var keepReading = false

    @Volatile
    private var isReconnecting = false

    @Volatile
    private var shouldAutoReconnect = true

    @Volatile
    private var isExitCommandInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        bindViews()
        setupLogin()
        setupButtons()

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (hasBluetoothPermission()) {
            startBluetoothConnection()
        } else {
            requestBluetoothPermission()
        }
    }

    private fun bindViews() {
        loginLayout = findViewById(R.id.loginLayout)
        controlLayout = findViewById(R.id.controlLayout)

        tvBtStatusLogin = findViewById(R.id.tvBtStatusLogin)
        tvBtStatusMain = findViewById(R.id.tvBtStatusMain)
        tvLastPacket = findViewById(R.id.tvLastPacket)

        etUserId = findViewById(R.id.etUserId)
        etPassword = findViewById(R.id.etPassword)

        btnLeftExit = findViewById(R.id.btnLeftExit)
        btnRightExit = findViewById(R.id.btnRightExit)
        btnStraightExit = findViewById(R.id.btnStraightExit)
        btnCancelExit = findViewById(R.id.btnCancelExit)

        updateExitButtonsState()
    }

    private fun setupLogin() {
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val id = etUserId.text.toString().trim()
            val pw = etPassword.text.toString().trim()

            if (id == "admin" && pw == "1234") {
                hideKeyboard()

                loginLayout.visibility = LinearLayout.GONE
                controlLayout.visibility = LinearLayout.VISIBLE

                Toast.makeText(this, "로그인 성공", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "아이디 또는 비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupButtons() {
        btnLeftExit.setOnClickListener {
            startExitCommand("LEFT_EXIT\n")
        }

        btnRightExit.setOnClickListener {
            startExitCommand("RIGHT_EXIT\n")
        }

        btnStraightExit.setOnClickListener {
            startExitCommand("STRAIGHT_EXIT\n")
        }

        btnCancelExit.setOnClickListener {
            applyButtonEnabledState(btnCancelExit, false)
            sendPacket("CANCEL_EXIT\n")
        }
    }

    private fun startExitCommand(packet: String) {
        if (isExitCommandInProgress) return

        isExitCommandInProgress = true
        updateExitButtonsState()
        sendPacket(packet)
    }

    private fun updateExitButtonsState() {
        val canSelectExitDirection = isConnected && !isExitCommandInProgress
        val canCancelExit = isConnected && isExitCommandInProgress

        applyButtonEnabledState(btnLeftExit, canSelectExitDirection)
        applyButtonEnabledState(btnRightExit, canSelectExitDirection)
        applyButtonEnabledState(btnStraightExit, canSelectExitDirection)
        applyButtonEnabledState(btnCancelExit, canCancelExit)
    }

    private fun setControlButtonsEnabled(enabled: Boolean) {
        if (enabled) {
            updateExitButtonsState()
            return
        }

        isExitCommandInProgress = false
        applyButtonEnabledState(btnLeftExit, false)
        applyButtonEnabledState(btnRightExit, false)
        applyButtonEnabledState(btnStraightExit, false)
        applyButtonEnabledState(btnCancelExit, false)
    }

    private fun applyButtonEnabledState(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.isClickable = enabled
        view.isFocusable = enabled

        view.alpha = if (enabled) {
            1.0f
        } else {
            0.35f
        }
    }

    private fun hideKeyboard() {
        val view = currentFocus

        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                REQUEST_BT_PERMISSION
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_BT_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_BT_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startBluetoothConnection()
            } else {
                updateBtStatus("블루투스 권한이 거부되었습니다.")
                setControlButtonsEnabled(false)
            }
        }
    }

    private fun startBluetoothConnection() {
        if (bluetoothAdapter == null) {
            updateBtStatus("이 기기는 블루투스를 지원하지 않습니다.")
            setControlButtonsEnabled(false)
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            updateBtStatus("블루투스가 꺼져 있습니다. 설정에서 켜주세요.")
            setControlButtonsEnabled(false)
            return
        }

        updateBtStatus("라즈베리파이에 연결 시도 중...")
        setControlButtonsEnabled(false)

        // 앱 시작 시에도 재연결 루프를 사용
        scheduleReconnect("app start")
    }

    private fun scheduleReconnect(reason: String) {
        if (!shouldAutoReconnect) return
        if (isReconnecting) return

        isConnected = false
        keepReading = false
        isReconnecting = true

        runOnUiThread {
            updateBtStatus("연결 끊김: $reason / 재연결 대기 중")
            setControlButtonsEnabled(false)
        }

        thread {
            closeBluetoothSocket()

            while (shouldAutoReconnect && !isConnected) {
                try {
                    Thread.sleep(3000)
                } catch (_: InterruptedException) {
                }

                if (!shouldAutoReconnect) break

                runOnUiThread {
                    updateBtStatus("라즈베리파이에 재연결 시도 중...")
                    setControlButtonsEnabled(false)
                }

                val success = connectToRaspberryPiOnce()

                if (!success && shouldAutoReconnect) {
                    runOnUiThread {
                        updateBtStatus("재연결 실패 / 3초 후 다시 시도")
                        setControlButtonsEnabled(false)
                    }
                }
            }

            isReconnecting = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToRaspberryPiOnce(): Boolean {
        return try {
            val adapter = bluetoothAdapter ?: return false
            val targetDevice = findTargetDevice(adapter)

            if (targetDevice == null) {
                runOnUiThread {
                    updateBtStatus("페어링된 라즈베리파이를 찾지 못했습니다.")
                    setControlButtonsEnabled(false)
                }
                return false
            }

            adapter.cancelDiscovery()

            closeBluetoothSocket()

            val socket = try {
                val s = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                s.connect()
                s
            } catch (e: IOException) {
                val fallback = targetDevice.javaClass
                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(targetDevice, 1) as BluetoothSocket

                fallback.connect()
                fallback
            }

            bluetoothSocket = socket
            outputStream = socket.outputStream

            isConnected = true
            keepReading = true

            runOnUiThread {
                val deviceName = targetDevice.name ?: targetDevice.address
                updateBtStatus("연결됨: $deviceName")
                isExitCommandInProgress = false
                updateExitButtonsState()
                Toast.makeText(this, "라즈베리파이 연결 성공", Toast.LENGTH_SHORT).show()
            }

            startReadLoop(socket)

            true

        } catch (e: Exception) {
            isConnected = false
            keepReading = false

            closeBluetoothSocket()

            runOnUiThread {
                updateBtStatus("연결 실패: ${e.message}")
                setControlButtonsEnabled(false)
            }

            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun findTargetDevice(adapter: BluetoothAdapter): BluetoothDevice? {
        val bondedDevices = adapter.bondedDevices ?: return null

        if (TARGET_DEVICE_MAC.isNotBlank()) {
            bondedDevices.firstOrNull {
                it.address.equals(TARGET_DEVICE_MAC, ignoreCase = true)
            }?.let {
                return it
            }
        }

        val possibleNames = listOf(
            TARGET_DEVICE_NAME,
            "PCA-RPI",
            "admin",
            "raspberrypi"
        )

        return bondedDevices.firstOrNull { device ->
            possibleNames.any { name ->
                name.isNotBlank() && device.name?.equals(name, ignoreCase = true) == true
            }
        }
    }

    private fun sendPacket(packet: String) {
        if (!isConnected || outputStream == null) {
            Toast.makeText(this, "블루투스가 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
            isExitCommandInProgress = false
            setControlButtonsEnabled(false)
            scheduleReconnect("not connected")
            return
        }

        thread {
            try {
                outputStream?.write(packet.toByteArray(Charsets.UTF_8))
                outputStream?.flush()

                runOnUiThread {
                    tvLastPacket.text = "송신 ${packet.trim()}"
                    Toast.makeText(this, "전송됨: ${packet.trim()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "패킷 전송 실패", Toast.LENGTH_SHORT).show()
                    isExitCommandInProgress = false
                    setControlButtonsEnabled(false)
                }

                scheduleReconnect(e.message ?: "send failed")
            }
        }
    }

    private fun startReadLoop(socket: BluetoothSocket) {
        thread {
            try {
                val reader = BufferedReader(
                    InputStreamReader(socket.inputStream, Charsets.UTF_8)
                )

                while (keepReading) {
                    val line = reader.readLine()

                    if (line == null) {
                        scheduleReconnect("socket closed")
                        break
                    }

                    handleReceivedPacket(line.trim())
                }

            } catch (e: IOException) {
                if (keepReading) {
                    scheduleReconnect(e.message ?: "socket error")
                }
            } finally {
                keepReading = false
                isConnected = false
            }
        }
    }

    private fun handleReceivedPacket(packet: String) {
        runOnUiThread {
            tvLastPacket.text = "수신 $packet"

            when (packet) {
                "EXIT_DONE" -> {
                    isExitCommandInProgress = false
                    updateExitButtonsState()
                    showAlert("출차 완료", "차량 출차가 완료되었습니다.")
                }

                "EXIT_CANCELED" -> {
                    isExitCommandInProgress = false
                    updateExitButtonsState()
                    showAlert("출차 취소", "출차가 취소되었습니다.")
                }

                else -> {
                    Toast.makeText(
                        this,
                        "알 수 없는 패킷 수신: $packet",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun closeBluetoothSocket() {
        try {
            outputStream?.close()
        } catch (_: IOException) {
        }

        try {
            bluetoothSocket?.close()
        } catch (_: IOException) {
        }

        outputStream = null
        bluetoothSocket = null
    }

    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun updateBtStatus(message: String) {
        tvBtStatusLogin.text = message
        tvBtStatusMain.text = message
    }

    override fun onDestroy() {
        super.onDestroy()

        shouldAutoReconnect = false
        keepReading = false
        isConnected = false
        isReconnecting = false
        isExitCommandInProgress = false

        closeBluetoothSocket()
    }
}