package io.sece.vlc.rcvr;

import java.nio.ByteBuffer;
import java.util.Collections;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


import org.opencv.android.CameraBridgeViewBase;

/*
 * This is a customized version of OpenCV's JavaCamera2View.
 */

/**
 * This class is an implementation of the Bridge View between OpenCV and Java Camera.
 * This class relays on the functionality available in base class and only implements
 * required functions:
 * connectCamera - opens Java camera and sets the PreviewCallback to be delivered.
 * disconnectCamera - closes the camera and stops preview.
 * When frame is delivered via callback from Camera - it processed via OpenCV to be
 * converted to RGBA32 and then passed to the external callback for modifications if required.
 */

@TargetApi(21)
public class JavaCamera2View extends CameraBridgeViewBase {

    private static final String LOGTAG = "JavaCamera2View";

    private ImageReader mImageReader;
    private int mPreviewFormat = ImageFormat.YUV_420_888;

    public int widthP = 640;
    public int heightP = 480;

    public CameraDevice mCameraDevice;

    private String mCameraID;
    private android.util.Size mPreviewSize = new android.util.Size(-1, -1);

    private HandlerThread mBackgroundThread;


    static double currExpTime= 0;

    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Handler mBackgroundHandler;
    private CameraCharacteristics characteristics;

    private Frame frame;

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            currExpTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
//            System.out.println("Exposure Time: " + currExpTime);
//            System.out.println("Frame Duration: " + result.get(CaptureResult.SENSOR_FRAME_DURATION));
        }
    };

    public JavaCamera2View(Context context, AttributeSet attrs) {
        super(context, attrs);
        frame = new Frame();

    }



    private void startBackgroundThread() {
        Log.i(LOGTAG, "startBackgroundThread");
        stopBackgroundThread();
        mBackgroundThread = new HandlerThread("OpenCVCameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        Log.i(LOGTAG, "stopBackgroundThread");
        if (mBackgroundThread == null)
            return;
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(LOGTAG, "stopBackgroundThread", e);
        }
    }

    protected void initializeCamera() {
        Log.i(LOGTAG, "initializeCamera");
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            assert manager != null;
            String camList[] = manager.getCameraIdList();
            if (camList.length == 0) {
                Log.e(LOGTAG, "Error: camera isn't detected.");
                return;
            }
            if (mCameraIndex == CameraBridgeViewBase.CAMERA_ID_ANY) {
                mCameraID = camList[0];
            } else {
                for (String cameraID : camList) {
                    characteristics = manager.getCameraCharacteristics(cameraID);
                    if ((mCameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK &&
                            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) ||
                        (mCameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT &&
                            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
                    ) {
                        mCameraID = cameraID;
                        break;
                    }
                }
            }
            if (mCameraID != null) {
                Log.i(LOGTAG, "Opening camera: " + mCameraID);
                manager.openCamera(mCameraID, mStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(LOGTAG, "OpenCamera - Camera Access Exception", e);
        } catch (IllegalArgumentException e) {
            Log.e(LOGTAG, "OpenCamera - Illegal Argument Exception", e);
        } catch (SecurityException e) {
            Log.e(LOGTAG, "OpenCamera - Security Exception", e);
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            mCameraDevice = null;
        }

    };

    @SuppressLint("Assert")
    private void createCameraPreviewSession() {
        final int w = widthP;//mPreviewSize.getWidth(), h = mPreviewSize.getHeight();
        final int h = heightP;
        Log.i(LOGTAG, "createCameraPreviewSession(" + w + "x" + h + ")");
        if (w < 0 || h < 0)
            return;
        try {
            if (null == mCameraDevice) {
                Log.e(LOGTAG, "createCameraPreviewSession: camera isn't opened");
                return;
            }
            if (null != mCaptureSession) {
                Log.e(LOGTAG, "createCameraPreviewSession: mCaptureSession is already started");
                return;
            }

            mImageReader = ImageReader.newInstance(w, h, mPreviewFormat, 2);
            mImageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image == null) return;

                try {
                    frame.setImage(image);
                } catch (Exception e) {
                    Log.e(LOGTAG, "Error while creating OpenCV frame", e);
                    image.close();
                    return;
                }

                // Note: After calling image.close(), the frame object is no longer usable until
                // setImage is called on it again. If any of the processing functions need to keep
                // a copy of the image data after deliverAndDrawFrame() returns, they need to
                // call frame.copy() to get a copy of the frame that does not depend on the image
                // object!

                deliverAndDrawFrame(frame);
                image.close();
            }, mBackgroundHandler);
            Surface surface = mImageReader.getSurface();

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Collections.singletonList(surface),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Log.i(LOGTAG, "createCaptureSession::onConfigured");
                        if (null == mCameraDevice) {
                            return; // camera is already closed
                        }
                        mCaptureSession = cameraCaptureSession;
                        try {
                            /*
                             * Setting device's auto focus off
                             */
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);

                            /*
                             * Setting device auto exposure Routine
                             */
//                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON);
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
//                            Rect zoom = new Rect(0, 0,
//                                    320,240);
//                            mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);

                            /*
                             * Setting device's auto white balancing routine
                             *
                             *
                             *
                             * */




                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(30, 30));

                            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);


                            Log.i(LOGTAG, "CameraPreviewSession has been started");
                        } catch (Exception e) {
//                            Log.e(TAG, "createCaptureSession failed", e);
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Log.e(LOGTAG, "createCameraPreviewSession failed");
                    }
                },
                null
            );
        } catch (CameraAccessException e) {
            Log.e(LOGTAG, "createCameraPreviewSession", e);
        }
    }

    @Override
    protected void disconnectCamera() {
        Log.i(LOGTAG, "closeCamera");
        try {
            CameraDevice c = mCameraDevice;
            mCameraDevice = null;
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != c) {
                c.close();
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } finally {
            stopBackgroundThread();
        }
    }

    @SuppressLint("Assert")
    boolean calcPreviewSize(final int width, final int height) {
        Log.i(LOGTAG, "calcPreviewSize: " + width + "x" + height);
        if (mCameraID == null) {
            Log.e(LOGTAG, "Camera isn't initialized!");
            return false;
        }
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            assert manager != null;
            characteristics = manager.getCameraCharacteristics(mCameraID);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);


            int bestWidth, bestHeight;
            float aspect = (float) width / height;
            assert map != null;
            android.util.Size[] sizes = map.getOutputSizes(ImageReader.class);
            bestWidth = sizes[0].getWidth();
            bestHeight = sizes[0].getHeight();
            for (android.util.Size sz : sizes) {
                int w = sz.getWidth(), h = sz.getHeight();
                Log.d(LOGTAG, "trying size: " + w + "x" + h);
                if (width >= w && height >= h && bestWidth <= w && bestHeight <= h
                        && Math.abs(aspect - (float) w / h) < 0.2) {
                    bestWidth = w;
                    bestHeight = h;
                }
            }
            Log.i(LOGTAG, "best size: " + bestWidth + "x" + bestHeight);
            assert(!(bestWidth == 0 || bestHeight == 0));
            if (mPreviewSize.getWidth() == bestWidth && mPreviewSize.getHeight() == bestHeight)
                return false;
            else {
                mPreviewSize = new android.util.Size(widthP, heightP);
                return true;
            }
        } catch (CameraAccessException e) {
            Log.e(LOGTAG, "calcPreviewSize - Camera Access Exception", e);
        } catch (IllegalArgumentException e) {
            Log.e(LOGTAG, "calcPreviewSize - Illegal Argument Exception", e);
        } catch (SecurityException e) {
            Log.e(LOGTAG, "calcPreviewSize - Security Exception", e);
        }
        return false;
    }

    @Override
    protected boolean connectCamera(int width, int height) {
        Log.i(LOGTAG, "setCameraPreviewSize(" + width + "x" + height + ")");
        startBackgroundThread();
        initializeCamera();
        try {
            boolean needReconfig = calcPreviewSize(width, height);
            mFrameWidth = mPreviewSize.getWidth();
            mFrameHeight = mPreviewSize.getHeight();

            if ((getLayoutParams().width == LayoutParams.MATCH_PARENT) && (getLayoutParams().height == LayoutParams.MATCH_PARENT))
                mScale = Math.min(((float)height)/mFrameHeight, ((float)width)/mFrameWidth);
            else
                mScale = 0;

            AllocateCache();

            if (needReconfig) {
                if (null != mCaptureSession) {
                    Log.d(LOGTAG, "closing existing previewSession");
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                createCameraPreviewSession();
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Interrupted while setCameraPreviewSize.", e);
        }

        enableExposureSettingUI();
        setManualExposureSettingUI();
        return true;
    }


    public void enableExposureSettingUI() {

        Range<Integer> controlAECompensationRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        assert controlAECompensationRange != null;
        System.out.println("CompensationRange: " + controlAECompensationRange.toString());
        System.out.println("ExposureRange: " + characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE).toString());
        double minCompensationRange = controlAECompensationRange.getLower();
        double maxCompensationRange = controlAECompensationRange.getUpper();

        Activity activity = (Activity)CameraFragment.context;
        activity.runOnUiThread(() -> {
            SeekBar seekbarExposure = activity.findViewById(R.id.seekbarExposure);
            seekbarExposure.setProgress((int)maxCompensationRange);
            seekbarExposure.setMax((int)(Math.abs(minCompensationRange) + maxCompensationRange));
            seekbarExposure.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    try{
                        System.out.println(progress);

                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, progress - (int)maxCompensationRange);

                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
                        ((Button)activity.findViewById(R.id.btManualExposure)).setText("Exp:\n" + (progress - (int)maxCompensationRange));
                    }catch(Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        });
    }

    public void setManualExposureSettingUI(){
        Activity activity = (Activity)CameraFragment.context;

        Button btManualExposure = (Button)activity.findViewById(R.id.btManualExposure);
        btManualExposure.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Range<Long> exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                System.out.println("Range "+exposureTimeRange.toString());

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle("Manual Exposure - Choose between " + exposureTimeRange.getLower() + " and " + exposureTimeRange.getUpper());
                final EditText input = new EditText(activity);
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                builder.setView(input);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        long curr_manual = Integer.parseInt(input.getText().toString());
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_OFF);
                        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (curr_manual));
                        try {
                            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                        Toast.makeText(activity, "Set to: " + curr_manual, Toast.LENGTH_SHORT).show();
                    }
                });

                builder.show();


            }
        });
    }


}
