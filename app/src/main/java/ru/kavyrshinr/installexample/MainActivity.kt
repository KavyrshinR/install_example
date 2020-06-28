package ru.kavyrshinr.installexample

import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageInstaller
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.InputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    companion object {
        const val PACKAGE_INSTALLED_ACTION = "ru.kavyrshinr.installexample.PACKAGE_INSTALLED_"

        const val LOG_TAG = "myLogs"

        const val FILE_CHOOSER_REQUEST_CODE = 123
        const val DEPRECATED_INSTALL_REQUEST_CODE = 120
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textViewStatus.setText(BuildConfig.VERSION_NAME)

        val preference = getSharedPreferences("preference", ContextWrapper.MODE_PRIVATE)
        val wasUpdated = preference.getBoolean("wasUpdated", false)

        textViewStatus.append(wasUpdated.toString())

        buttonInstall.setOnClickListener { showFileChooser() }
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        startActivityForResult(Intent.createChooser(intent, "Select file chooser"), FILE_CHOOSER_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(LOG_TAG, "Request code: ${requestCode} Result code ${resultCode}")
        Log.d(LOG_TAG, "Data: " + data.toString())

        if (resultCode != Activity.RESULT_OK) {
            return
        }

        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val uri = data?.data ?: return

//            install(contentResolver.openInputStream(uri) ?: return)

            val targetFile = simulateDownload(uri)

            val exposedUri = Uri.fromFile(targetFile)

            val grantedUri = MediaFileProvider.getUriForFile(this, MediaFileProvider.AUTHORITIES, targetFile)

            Log.d(LOG_TAG, "\n Source: ${uri} \n Exposed: ${exposedUri} \n Granted: ${grantedUri}")

            xiaomiInstall(grantedUri)
        }
    }

    private fun simulateDownload(uri: Uri): File {
        val externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: throw Exception("No have directory")
        externalFilesDir.mkdirs()
        val targetFile = File(externalFilesDir, "temp.apk")

        val inputStream = contentResolver.openInputStream(uri) ?: throw Exception()

        targetFile.outputStream().use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }

        return targetFile
    }

    private fun xiaomiInstall(uri: Uri) {
        startActivityForResult(Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setData(uri)
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                DEPRECATED_INSTALL_REQUEST_CODE)
    }

    private fun install(apkStream: InputStream) {
        val intentFilter = IntentFilter()
        intentFilter.addAction(PACKAGE_INSTALLED_ACTION + "1")
        registerReceiver(CallBackReceiver(1), intentFilter)

        val packageInstaller = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
//        notifyer.onNext(Downloading(0))

//        val fileInputStream = FileInputStream(newVersion)

        session.openWrite("package", 0, -1).use { output -> //TODO Length
            apkStream.use { inputStream ->
                inputStream.copyTo(output)
                session.fsync(output)
            }
        }


        val intent = Intent(PACKAGE_INSTALLED_ACTION + "1")
        val pendingIntent = PendingIntent.getBroadcast(this, 1, intent, 0)
        val statusReceiver = pendingIntent.intentSender
//        notifyer.onNext(Installing)
        session.commit(statusReceiver)
        session.close()
    }

    private inner class CallBackReceiver(private val id: Int) : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent) {
            Log.d(LOG_TAG, intent.toString())
            if ((PACKAGE_INSTALLED_ACTION + id) == intent.action) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                Log.d(LOG_TAG, "Status ${status}")
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        // This test app isn't privileged, so the user has to confirm the install.
                        val confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT) as Intent? ?: return
                        Log.d(LOG_TAG, confirmIntent.toString())
//                        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(confirmIntent)
                    }
                    PackageInstaller.STATUS_SUCCESS -> {
                        Toast.makeText(this@MainActivity, "STATUS_SUCCESS", Toast.LENGTH_LONG).show()
//                        idMappings.get()[id]?.apply {
//                            installationProcess.onNext(Complete)
//                            sessionId = null
//                        }
//                        this@AppInstallerNormal.context.unregisterReceiver(this)
                    }
                    PackageInstaller.STATUS_FAILURE_ABORTED -> {
                        Toast.makeText(this@MainActivity, "STATUS_FAILURE_ABORTED", Toast.LENGTH_LONG).show()
//                        idMappings.get()[id]?.apply {
//                            installationProcess.onNext(Complete)
//                            sessionId = null
//                        }
//                        this@AppInstallerNormal.context.unregisterReceiver(this)
                    }
                    else -> {
                        when (status) {
                            PackageInstaller.STATUS_FAILURE -> {
                                val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                Toast.makeText(this@MainActivity, "STATUS_FAILURE message: ${statusMessage}", Toast.LENGTH_LONG).show()
                                Log.d(LOG_TAG, "Failure message: ${statusMessage}")
                            }
                            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                                Toast.makeText(this@MainActivity, "STATUS_FAILURE_ABORTED", Toast.LENGTH_LONG).show()
                                Log.d(LOG_TAG, "Aborted")
                            }
                            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                                Toast.makeText(this@MainActivity, "STATUS_FAILURE_BLOCKED", Toast.LENGTH_LONG).show()
                                Log.d(LOG_TAG, "Blocked")
                            }
                            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                                val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                val otherPackageName = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)

                                Toast.makeText(this@MainActivity, "STATUS_FAILURE_CONFLICT ${statusMessage} \n" +
                                        " ${otherPackageName}", Toast.LENGTH_LONG).show()
                                Log.d(LOG_TAG, "Conflict ${statusMessage} \n ${otherPackageName}")
                            }
                            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                                Toast.makeText(this@MainActivity, "STATUS_FAILURE_INCOMPATIBLE", Toast.LENGTH_LONG).show()
                                Log.d(LOG_TAG, "Incompatible")
                            }
                            PackageInstaller.STATUS_FAILURE_INVALID -> {
                                Toast.makeText(this@MainActivity, "STATUS_FAILURE_INVALID", Toast.LENGTH_LONG).show()
                                Log.d(LOG_TAG, "Invalid")
                            }
                            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                                Toast.makeText(this@MainActivity, "STATUS_FAILURE_STORAGE", Toast.LENGTH_LONG).show()
                                Log.d(LOG_TAG, "Failure storage")
                            }
                            else -> {
                                Toast.makeText(this@MainActivity, "STATUS_UNEXPECTED", Toast.LENGTH_LONG).show()
                                Log.d(LOG_TAG, "Unexpected error")
                            }
                        }

//                        this@AppInstallerNormal.context.unregisterReceiver(this)
                    }
                }
            }
        }
    }
}
