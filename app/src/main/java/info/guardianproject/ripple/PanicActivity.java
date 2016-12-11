package info.guardianproject.ripple;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import info.guardianproject.panic.PanicTrigger;

public class PanicActivity extends Activity implements OnTouchListener {
    public static final String TAG = "PanicActivity";

    public static final String EXTRA_TEST_RUN = "info.guardianproject.ripple.extra.TEST_RUN";

    private int yMaxTranslation;
    private int yCurrentTranslation;
    private int yDelta;
    private boolean mReleaseWillTrigger = false;
    private RelativeLayout mFrameRoot;
    private ImageView mPanicSwipeButton;
    private TextView mTextHint;
    private ImageView mSwipeArrows;
    private RippleDrawingView mRipples;
    private int mColorWhite;
    private int mColorRipple;
    private int mColorTriggeredText;
    private int mRedStart;
    private int mGreenStart;
    private int mBlueStart;
    private int mRedDelta;
    private int mGreenDelta;
    private int mBlueDelta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setBackgroundDrawable(null);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_panic);

        mFrameRoot = (RelativeLayout) findViewById(R.id.frameRoot);
        mTextHint = (TextView) findViewById(R.id.textHint);
        mSwipeArrows = (ImageView) findViewById(R.id.swipe_arrows);
        mRipples = (RippleDrawingView) findViewById(R.id.ripples);
        mPanicSwipeButton = (ImageView) findViewById(R.id.panic_swipe_button);
        mPanicSwipeButton.setOnTouchListener(this);

        View btnCancel = findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                PanicActivity.this.finish();
            }
        });

        Resources r = getResources();
        mColorWhite = r.getColor(android.R.color.white);
        mColorRipple = r.getColor(R.color.ripple);
        int colorTriggered = r.getColor(R.color.triggered);
        mColorTriggeredText = r.getColor(R.color.triggered_text);
        mRedStart = (mColorRipple & 0x00ff0000) >> 16;
        mGreenStart = (mColorRipple & 0x0000ff00) >> 8;
        mBlueStart = mColorRipple & 0x000000ff;
        int redEnd = (colorTriggered & 0x00ff0000) >> 16;
        int greenEnd = (colorTriggered & 0x0000ff00) >> 8;
        int blueEnd = colorTriggered & 0x000000ff;
        mRedDelta = redEnd - mRedStart;
        mGreenDelta = greenEnd - mGreenStart;
        mBlueDelta = blueEnd - mBlueStart;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // if the user navigates away, reset the trigger process
        finish();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (view == mPanicSwipeButton) {
            final int Y = (int) event.getRawY();
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    mPanicSwipeButton.setPressed(true);

                    RelativeLayout.LayoutParams lParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
                    yDelta = Y - lParams.topMargin;
                    mReleaseWillTrigger = false;

                    Rect mArrowRect = new Rect();
                    if (!mSwipeArrows.getGlobalVisibleRect(mArrowRect)) {
                        mArrowRect = null;
                    } else {
                        Rect symbolRect = new Rect();
                        if (mPanicSwipeButton.getGlobalVisibleRect(symbolRect)) {
                            yMaxTranslation = mArrowRect.bottom - symbolRect.bottom;
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    mPanicSwipeButton.setPressed(false);
                    mRipples.setSize(0);
                    mRipples.invalidate();

                    if (mReleaseWillTrigger) {
                        SharedPreferences prefs =
                                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                        if (prefs.getBoolean(getString(R.string.pref_countdown_enabled), false)) {
                            AnimationHelpers.scale(mPanicSwipeButton, 1.0f, 0, 200, new Runnable() {
                                @Override
                                public void run() {
                                    Intent intent = new Intent(getBaseContext(), CountDownActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                    intent.putExtra(EXTRA_TEST_RUN,
                                            getIntent().getBooleanExtra(EXTRA_TEST_RUN, false));
                                    startActivity(intent);
                                    finish();
                                }
                            });
                        } else {
                            PanicTrigger.sendTrigger(PanicActivity.this);
                            Toast.makeText(PanicActivity.this, R.string.done, Toast.LENGTH_LONG).show();

                            /* This app needs to stay running for a while to make sure that it sends
                             * all of the Intents to Activities, Services, and BroadcastReceivers. If
                             * it exits too soon, they will not get sent. */
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    ExitActivity.exitAndRemoveFromRecentApps(PanicActivity.this);
                                }
                            }, 10000); // 10 second delay
                        }
                    } else {
                        AnimationHelpers.translateY(mPanicSwipeButton, yCurrentTranslation, 0, 200);
                        mFrameRoot.setBackgroundColor(mColorRipple);
                    }
                    mReleaseWillTrigger = false;
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    break;

                case MotionEvent.ACTION_MOVE:
                    yCurrentTranslation = Math.max(0, Math.min(Y - yDelta, yMaxTranslation));
                    AnimationHelpers.translateY(mPanicSwipeButton, yCurrentTranslation, yCurrentTranslation, 0);

                    float v = (float) yCurrentTranslation / yMaxTranslation;
                    mFrameRoot.setBackgroundColor((int) (0xff000000 + ((mRedStart + ((int) (mRedDelta * v))) << 16)
                            + ((mGreenStart + ((int) (mGreenDelta * v))) << 8)
                            + (mBlueStart + (mBlueDelta * v))));

                    int rippleSize = yMaxTranslation / 2;
                    if (yCurrentTranslation > rippleSize) {
                        float k = rippleSize / (yMaxTranslation - rippleSize);
                        mRipples.setSize((yCurrentTranslation - rippleSize) * k);
                        mRipples.invalidate();
                    }

                    if (yCurrentTranslation == yMaxTranslation) {
                        mReleaseWillTrigger = true;
                        mTextHint.setText(R.string.release_to_confirm);
                        mTextHint.setTextColor(mColorTriggeredText);
                    } else {
                        mReleaseWillTrigger = false;
                        mTextHint.setText(R.string.swipe_down_to_trigger);
                        mTextHint.setTextColor(mColorWhite);
                    }
                    break;
            }
            view.invalidate();
            return true;
        }
        return false;
    }
}
