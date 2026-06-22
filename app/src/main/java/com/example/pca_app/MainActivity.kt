package com.example.pca_app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

        /*
         * 라즈베리파이 Bluetooth 이름 또는 MAC 주소를 설정하세요.
         *
         * Android 설정 > Bluetooth에서 보이는 라즈베리파이 이름이
         * raspberrypi가 아니면 TARGET_DEVICE_NAME을 수정하세요.
         *
         * MAC 주소를 알면 TARGET_DEVICE_MAC에 넣는 것이 더 안정적입니다.
         * 예: private const val TARGET_DEVICE_MAC = "B8:27:EB:AA:BB:CC"
         */
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

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    @Volatile
    private var isConnected = false

    @Volatile
    private var keepReading = false

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
    }

    private fun setupLogin() {
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val id = etUserId.text.toString().trim()
            val pw = etPassword.text.toString().trim()

            if (id == "admin" && pw == "1234") {
                loginLayout.visibility = LinearLayout.GONE
                controlLayout.visibility = LinearLayout.VISIBLE

                Toast.makeText(this, "로그인 성공", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "아이디 또는 비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnLeftExit).setOnClickListener {
            sendPacket("LEFT_EXIT\n")
        }

        findViewById<Button>(R.id.btnRightExit).setOnClickListener {
            sendPacket("RIGHT_EXIT\n")
        }

        findViewById<Button>(R.id.btnStraightExit).setOnClickListener {
            sendPacket("STRAIGHT_EXIT\n")
        }

        findViewById<Button>(R.id.btnCancelExit).setOnClickListener {
            sendPacket("CANCEL_EXIT\n")
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
            }
        }
    }

    private fun startBluetoothConnection() {
        if (bluetoothAdapter == null) {
            updateBtStatus("이 기기는 블루투스를 지원하지 않습니다.")
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            updateBtStatus("블루투스가 꺼져 있습니다. 설정에서 켜주세요.")
            return
        }

        updateBtStatus("라즈베리파이에 연결 시도 중...")

        thread {
            connectToRaspberryPi()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToRaspberryPi() {
        try {
            val adapter = bluetoothAdapter ?: return
            val targetDevice = findTargetDevice(adapter)

            if (targetDevice == null) {
                runOnUiThread {
                    updateBtStatus("페어링된 라즈베리파이를 찾지 못했습니다.")
                    Toast.makeText(
                        this,
                        "먼저 Android Bluetooth 설정에서 Raspberry Pi와 페어링하세요.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }

            adapter.cancelDiscovery()

            val socket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            bluetoothSocket = socket
            outputStream = socket.outputStream
            isConnected = true
            keepReading = true

            runOnUiThread {
                val deviceName = targetDevice.name ?: targetDevice.address
                updateBtStatus("연결됨: $deviceName")
                Toast.makeText(this, "라즈베리파이 연결 성공", Toast.LENGTH_SHORT).show()
            }

            startReadLoop(socket)

        } catch (e: IOException) {
            isConnected = false
            keepReading = false

            try {
                bluetoothSocket?.close()
            } catch (_: IOException) {
            }

            runOnUiThread {
                updateBtStatus("연결 실패: ${e.message}")
            }
        } catch (e: SecurityException) {
            runOnUiThread {
                updateBtStatus("블루투스 권한 오류: ${e.message}")
            }
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

        return bondedDevices.firstOrNull {
            it.name?.equals(TARGET_DEVICE_NAME, ignoreCase = true) == true
        }
    }

    private fun sendPacket(packet: String) {
        if (!isConnected || outputStream == null) {
            Toast.makeText(this, "블루투스가 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        thread {
            try {
                outputStream?.write(packet.toByteArray(Charsets.UTF_8))
                outputStream?.flush()

                runOnUiThread {
                    tvLastPacket.text = "최근 송신 패킷: ${packet.trim()}"
                    Toast.makeText(this, "전송됨: ${packet.trim()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                isConnected = false

                runOnUiThread {
                    updateBtStatus("전송 실패: ${e.message}")
                    Toast.makeText(this, "패킷 전송 실패", Toast.LENGTH_SHORT).show()
                }
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
                    val line = reader.readLine() ?: break
                    handleReceivedPacket(line.trim())
                }

            } catch (e: IOException) {
                if (keepReading) {
                    runOnUiThread {
                        updateBtStatus("수신 연결 끊김: ${e.message}")
                    }
                }
            } finally {
                isConnected = false
                keepReading = false
            }
        }
    }

    private fun handleReceivedPacket(packet: String) {
        runOnUiThread {
            tvLastPacket.text = "최근 수신 패킷: $packet"

            when (packet) {
                "EXIT_DONE" -> {
                    showAlert("출차 완료", "차량 출차가 완료되었습니다.")
                }

                "EXIT_CANCELED" -> {
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

    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun updateBtStatus(message: String) {
        tvBtStatusLogin.text = message
        tvBtStatusMain.text = "블루투스 상태: $message"
    }

    override fun onDestroy() {
        super.onDestroy()

        keepReading = false
        isConnected = false

        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (_: IOException) {
        }
    }
}