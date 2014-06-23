package com.yoctopuce.example.yocto_pool;

import com.yoctopuce.YoctoAPI.YAPI;
import com.yoctopuce.YoctoAPI.YAPI_Exception;
import com.yoctopuce.YoctoAPI.YRelay;
import com.yoctopuce.example.yocto_pool.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class PoolControlActivity extends Activity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;
    private TextView _statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pool_control);

        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        _statusView = (TextView) findViewById(R.id.fullscreen_content);

        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.
        mSystemUiHider = SystemUiHider.getInstance(this, _statusView, HIDER_FLAGS);
        mSystemUiHider.setup();
        mSystemUiHider
                .setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
                    // Cached values.
                    int mControlsHeight;
                    int mShortAnimTime;

                    @Override
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
                    public void onVisibilityChange(boolean visible) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                            // If the ViewPropertyAnimator API is available
                            // (Honeycomb MR2 and later), use it to animate the
                            // in-layout UI controls at the bottom of the
                            // screen.
                            if (mControlsHeight == 0) {
                                mControlsHeight = controlsView.getHeight();
                            }
                            if (mShortAnimTime == 0) {
                                mShortAnimTime = getResources().getInteger(
                                        android.R.integer.config_shortAnimTime);
                            }
                            controlsView.animate()
                                    .translationY(visible ? 0 : mControlsHeight)
                                    .setDuration(mShortAnimTime);
                        } else {
                            // If the ViewPropertyAnimator APIs aren't
                            // available, simply show or hide the in-layout UI
                            // controls.
                            controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
                        }

                        if (visible && AUTO_HIDE) {
                            // Schedule a hide().
                            delayedHide(AUTO_HIDE_DELAY_MILLIS);
                        }
                    }
                });

        // Set up the user interaction to manually show or hide the system UI.
        _statusView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TOGGLE_ON_CLICK) {
                    mSystemUiHider.toggle();
                } else {
                    mSystemUiHider.show();
                }
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.open_button).setOnTouchListener(mDelayHideTouchListener);
        findViewById(R.id.close_button).setOnTouchListener(mDelayHideTouchListener);
        findViewById(R.id.release_button).setOnTouchListener(mDelayHideTouchListener);
        _statusView.setText(R.string.disconnected);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    @Override
    protected void onResume() {
        super.onResume();
        new BgYoctoTasks().execute(BgYoctoTasks.GET_POOL_STATE);

    }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    Handler mHideHandler = new Handler();
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    public void closePool(View view) {
        new BgYoctoTasks().execute(BgYoctoTasks.CLOSE_POOL);
    }

    public void openPool(View view) {
        new BgYoctoTasks().execute(BgYoctoTasks.OPEN_POOL);
    }

    public void releasePool(View view) {
        new BgYoctoTasks().execute(BgYoctoTasks.RELEASE_POOL);
    }




    public class BgYoctoTasks extends AsyncTask<Integer, String, String> {

        public static final int GET_POOL_STATE = 2;
        public static final int CLOSE_POOL = 3;
        public static final int OPEN_POOL= 4;
        public static final int RELEASE_POOL = 5;

        private YRelay pool_key;
        private YRelay pool_control;
        public static final String RESULT_RELEASED = "RELEASED";
        public static final String RESULT_OPEN = "OPEN";
        public static final String RESULT_CLOSE = "CLOSE";


        @Override
        protected String doInBackground(Integer... cmd)
        {
            String res = "";
            publishProgress(".");
            try {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(PoolControlActivity.this);
                String host = sharedPref.getString("prefHost", "net");
                String user = sharedPref.getString("prefUsername","");
                String pass = sharedPref.getString("prefPassword","");
                String url;
                if (user.length()>0){
                    if (pass.length()>0){
                        url = user+":"+pass+"@"+host;
                    }else {
                        url = user+"@"+host;
                    }
                }else {
                    url = host;
                }
                publishProgress("..");
                YAPI.RegisterHub(url);
                pool_key = YRelay.FindRelay("pool_key");
                pool_control = YRelay.FindRelay("pool_control");
                publishProgress("...");
                switch (cmd[0]) {
                    case GET_POOL_STATE:
                        res = getPoolState();
                        break;
                    case CLOSE_POOL:
                        pool_key.set_state(YRelay.STATE_B);
                        pool_control.set_state(YRelay.STATE_A);
                        res = getPoolState();
                        break;
                    case OPEN_POOL:
                        pool_key.set_state(YRelay.STATE_B);
                        pool_control.set_state(YRelay.STATE_B);
                        res = getPoolState();
                        break;
                    case RELEASE_POOL:
                        pool_key.set_state(YRelay.STATE_A);
                        res = getPoolState();
                        break;
                    default:
                        break;
                }
            } catch (YAPI_Exception e) {
                e.printStackTrace();
                res = e.getLocalizedMessage();
            }
            publishProgress(".....");
            YAPI.FreeAPI();
            publishProgress("......");
            return res;
        }

        private String getPoolState() throws YAPI_Exception {
            publishProgress("....");
            if(pool_key.get_state() == YRelay.STATE_B) {
                if (pool_control.get_state() == YRelay.STATE_B) {
                    return RESULT_OPEN;
                }else{
                    return RESULT_CLOSE;
                }
            }
            return RESULT_RELEASED;
        }


        @Override
        protected void onProgressUpdate(String... values) {
            _statusView.setText(values[values.length-1]);
        }

        @Override
        protected void onPostExecute(String result) {
            if(result == RESULT_RELEASED) {
                _statusView.setText(R.string.released);
            }else if(result == RESULT_OPEN) {
                _statusView.setText(R.string.opened);
            }else if(result == RESULT_CLOSE) {
                _statusView.setText(R.string.closed);
            }else{
                _statusView.setText(result);
            }
        }


    }




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
