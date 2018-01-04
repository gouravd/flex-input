package com.lytefast.flexinput.fragment

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.MediaStore
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import com.lytefast.flexinput.FlexInputCoordinator
import com.lytefast.flexinput.R
import com.lytefast.flexinput.utils.FileUtils.toAttachment
import com.wonderkiln.camerakit.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


/**
 * [Fragment] that allows the user to take a live photo directly from the camera.
 *
 * @author Sam Shih
 */
open class CameraFragment : PermissionsFragment() {

  protected var cameraContainer: View? = null
  protected var cameraView: CameraView? = null
  protected var permissionsContainer: FrameLayout? = null
  protected var cameraFacingBtn: ImageView? = null

  private var flexInputCoordinator: FlexInputCoordinator<Any>? = null

  /**
   * Temporary holder for when we intent to the camera. This is used because the resulting intent
   * doesn't return any data if you set the output file in the request.
   */
  private var photoFile: File? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    retainInstance = true
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    @Suppress("UNCHECKED_CAST")
    this.flexInputCoordinator = parentFragment?.parentFragment as? FlexInputCoordinator<Any>

    return inflater.inflate(R.layout.fragment_camera, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    view.apply {
      permissionsContainer = findViewById(R.id.permissions_container)
      cameraContainer = findViewById(R.id.camera_container)
      cameraView = findViewById<CameraView>(R.id.camera_view)?.also { safeCameraView ->

        findViewById<View>(R.id.camera_view_cropper)
            ?.setOnTouchListener { _, _ -> true  /* disable user scroll*/ }
        findViewById<View>(R.id.take_photo_btn)
            ?.setOnClickListener { onTakePhotoClick(safeCameraView) }
        findViewById<View>(R.id.launch_camera_btn)
            ?.setOnClickListener { onLaunchCameraClick() }
        findViewById<ImageView>(R.id.camera_flash_btn)
            ?.setOnClickListener { onCameraFlashClick(it as ImageView) }
        cameraFacingBtn = findViewById(R.id.camera_facing_btn)
        cameraFacingBtn?.setOnClickListener { onCameraFacingClick() }

        safeCameraView.addCameraKitListener(cameraCallback)
      }
    }
    tryStartCamera()
  }

  override fun onResume() {
    super.onResume()

    val context = context ?: return

    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
        || !hasPermissions(*REQUIRED_PERMISSIONS)) {
      cameraContainer?.visibility = View.GONE
      permissionsContainer?.also {
        it.visibility = View.VISIBLE
        initPermissionsView(it)
      }

      return   // No camera detected. just chill
    }
    cameraContainer?.visibility = View.VISIBLE
    permissionsContainer?.visibility = View.GONE

    // Delayed restart since we are coming back from camera, and for some reason the API
    // isn't fast enough to acknowledge the other activity closed the camera.
    cameraView?.postDelayed({
      tryStartCamera()
    }, 350)
  }

  protected open fun initPermissionsView(permissionsContainer: FrameLayout) {
    val view = LayoutInflater.from(permissionsContainer.context)
        .inflate(R.layout.view_camera_permissions, permissionsContainer, true)
    view.findViewById<View>(R.id.permissions_req_btn)
        ?.setOnClickListener { requestPermissionClick() }
  }

  /**
   * Some cameras don't properly set the [android.hardware.Camera.CameraInfo.facing] value.
   * So here, if we fail, just try getting the first front facing camera.
   */
  private fun tryStartCamera() {
    try {
      if (cameraView?.isStarted == true) {
        cameraView?.stop()
      }
      cameraView?.start()
    } catch (e: Exception) {
      Log.w(TAG, "Camera could not be loaded, try front facing camera", e)

      try {
        cameraView?.facing = CameraKit.Constants.FACING_FRONT
        cameraView?.start()
      } catch (ex: Exception) {
        Log.e(TAG, "Camera could not be loaded", e)
      }
    }

    cameraContainer?.findViewById<ImageView>(R.id.camera_flash_btn)?.also {
      setFlash(it, CameraKit.Constants.FLASH_AUTO)
    }
  }

  override fun onPause() {
    cameraView?.stop()
    super.onPause()
  }

  protected open fun requestPermissionClick() {
    requestPermissions(object : PermissionsFragment.PermissionsResultCallback {
      override fun granted() {
        cameraView?.post {
          // #onResume will take care of this for us
        }
      }

      override fun denied() {}
    }, *REQUIRED_PERMISSIONS)
  }

  private fun onCameraFlashClick(flashBtn: ImageView) {
    val currentFlashState = cameraView?.flash
    val currentStateIndex = FLASH_STATE_CYCLE_LIST.indices.firstOrNull {
      currentFlashState == FLASH_STATE_CYCLE_LIST[it]
    } ?: 0

    val newStateIndex = (currentStateIndex + 1) % FLASH_STATE_CYCLE_LIST.size
    setFlash(flashBtn, FLASH_STATE_CYCLE_LIST[newStateIndex])
  }


  private fun onCameraFacingClick() {
    val currentFlashState = cameraView?.facing
    val currentStateIndex = FACING_STATE_CYCLE_LIST.indices.firstOrNull {
      currentFlashState == FACING_STATE_CYCLE_LIST[it]
    } ?: 0

    val newStateIndex = (currentStateIndex + 1) % FACING_STATE_CYCLE_LIST.size
    setFacing(FACING_STATE_CYCLE_LIST[newStateIndex])
  }

  private fun onTakePhotoClick(cameraView: CameraView) {
    if (!cameraView.isStarted) {
      return
    }

    cameraView.captureImage { cameraKitImage ->
      val data = cameraKitImage.jpeg
      Log.d(TAG, "onPictureTaken ${data?.size ?: 0}")
      if (data == null) {
        return@captureImage
      }

      val context = context ?: return@captureImage
      Toast.makeText(context, "Picture saved", Toast.LENGTH_SHORT).show()

      AsyncTask.execute {
        flexInputCoordinator?.fileManager?.newImageFile()?.also { file ->
          try {
            FileOutputStream(file).use {
              it.write(data)
            }

            context.addToMediaStore(file)
            cameraView.post {
              flexInputCoordinator?.addExternalAttachment(file.toAttachment())
            }
          } catch (e: IOException) {
            Log.w(TAG, "Cannot write to $file", e)
          }
        }
      }
    }
  }

  private fun onLaunchCameraClick() {
    val context = context ?: return
    cameraView?.stop()

    photoFile = flexInputCoordinator!!.fileManager.newImageFile()
    val photoUri = flexInputCoordinator!!.fileManager.toFileProviderUri(context, photoFile)
    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        .putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

    if (takePictureIntent.resolveActivity(context.packageManager) != null) {
      context.grantWriteAccessToURI(takePictureIntent, photoUri)
      startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (REQUEST_IMAGE_CAPTURE != requestCode) {
      return
    }

    photoFile?.also {
      when {
        Activity.RESULT_CANCELED == resultCode -> { /* Do nothing*/ }
        Activity.RESULT_OK != resultCode -> {
          Toast.makeText(context, R.string.camera_intent_result_error, Toast.LENGTH_SHORT).show()
          it.delete()  // cleanup
        }
        else -> {
          context?.addToMediaStore(it)
          flexInputCoordinator?.addExternalAttachment(it.toAttachment())
        }
      }
    }
  }

  private val cameraCallback = object : CameraKitEventListener {
    override fun onVideo(cameraKitVideo: CameraKitVideo) {
      TODO("not implemented")
    }

    override fun onEvent(cameraKitEvent: CameraKitEvent) {
      when (cameraKitEvent.type) {
        CameraKitEvent.TYPE_CAMERA_OPEN -> {
          Log.d(TAG, "onCameraOpened")
          cameraView?.apply { onFacingChanged(facing) }
        }
        CameraKitEvent.TYPE_CAMERA_CLOSE -> Log.d(TAG, "onCameraClosed")
        // TODO: once CameraKit implements TYPE_FACING_CHANGED & TYPE_FLASH_CHANGED handle
      }
    }

    override fun onError(cameraKitError: CameraKitError) {
      Log.d(TAG, "onCameraError ${cameraKitError.exception}")
    }

    override fun onImage(cameraKitImage: CameraKitImage) {
      val data = cameraKitImage.jpeg

      Log.d(TAG, "onPictureTaken ${data?.size ?: 0}")
      if (data == null) return
      Toast.makeText(context, "Picture saved", Toast.LENGTH_SHORT).show()

      AsyncTask.execute {
        flexInputCoordinator?.fileManager?.newImageFile()?.also { file ->
          try {
            FileOutputStream(file).use {
              it.write(data)
            }

            context?.addToMediaStore(file)
            view?.post {
              flexInputCoordinator?.addExternalAttachment(file.toAttachment())
            }
          } catch (e: IOException) {
            Log.w(TAG, "Cannot write to $file", e)
          }
        }
      }
    }
  }

  private fun setFacing(newFacingState: Int) {
    cameraView?.apply {
      if (facing != newFacingState) {
        facing = newFacingState
        Toast.makeText(context, R.string.camera_switched, Toast.LENGTH_SHORT).show()
      }
    }
    onFacingChanged(newFacingState)
  }

  private fun onFacingChanged(newFacingState: Int) {
    @DrawableRes val facingImg: Int =
        when (newFacingState) {
          CameraKit.Constants.FACING_FRONT -> R.drawable.ic_camera_rear_white_24dp
//          CameraView.FACING_BACK,
          else -> R.drawable.ic_camera_front_white_24dp
        }
    cameraFacingBtn?.setImageResource(facingImg)
  }

  private fun setFlash(btn: ImageView, newFlashState: Int) {
    if (cameraView?.flash == newFlashState) {
      return
    }

    @DrawableRes val flashImage: Int
    @StringRes val flashMsg: Int
    when (newFlashState) {
      CameraKit.Constants.FLASH_ON -> {
        flashMsg = R.string.flash_on
        flashImage = R.drawable.ic_flash_on_24dp
      }
      CameraKit.Constants.FLASH_OFF -> {
        flashMsg = R.string.flash_off
        flashImage = R.drawable.ic_flash_off_24dp
      }
      else -> {
        flashMsg = R.string.flash_auto
        flashImage = R.drawable.ic_flash_auto_24dp
      }
    }

    cameraView?.flash = newFlashState
    Toast.makeText(btn.context, flashMsg, Toast.LENGTH_SHORT).show()
    btn.setImageResource(flashImage)
  }

  companion object {
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA)

    private val FLASH_STATE_CYCLE_LIST = intArrayOf(
        CameraKit.Constants.FLASH_AUTO,
        CameraKit.Constants.FLASH_ON,
        CameraKit.Constants.FLASH_OFF)

    private val FACING_STATE_CYCLE_LIST = intArrayOf(
        CameraKit.Constants.FACING_BACK,
        CameraKit.Constants.FACING_FRONT)

    private val TAG = CameraFragment::class.java.canonicalName

    val REQUEST_IMAGE_CAPTURE = 4567

    /**
     * This is a hack to allow the file provider API to still
     * work on older API versions.
     *
     * http://bit.ly/2iC4bUJ
     */
    private fun Context.grantWriteAccessToURI(intent: Intent, uri: Uri) {
      val resInfoList =
          packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

      for (resolveInfo in resInfoList) {
        val packageName = resolveInfo.activityInfo.packageName
        val mode = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION

        grantUriPermission(packageName, uri, mode)
      }
    }


    private fun Context.addToMediaStore(photo: File) {
      val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(photo))
      sendBroadcast(mediaScanIntent)
      Log.d(TAG, "Photo added to MediaStore: ${photo.name}")
    }
  }
}
