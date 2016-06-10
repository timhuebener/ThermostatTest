package thermocompany.thermostat;

import android.content.Intent;
import android.os.CountDownTimer;
import android.app.Activity;
import android.os.Handler;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.net.ConnectException;

import util.*;

public class MainActivity extends AppCompatActivity {

    TextView tempTarget;
    TextView tempCurrent;
    double targetTemperature;
    double currentTemperature;
    Button plus;
    Button minus;
    CountDownTimer refreshTimer;
    ToggleButton holdButton;
    Handler repeatHandler;
    Runnable repeatPlus;
    Runnable repeatMinus;
    final int CLICK_INTERVAL = 300;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        HeatingSystem.BASE_ADDRESS = "http://wwwis.win.tue.nl/2id40-ws/60";
        tempTarget = (TextView) findViewById(R.id.temp);
        tempCurrent = (TextView) findViewById(R.id.tempActual);
        plus = (Button) findViewById(R.id.plus);
        minus = (Button) findViewById(R.id.minus);
        holdButton = (ToggleButton) findViewById(R.id.BtnHold);
        plus.setLongClickable(true);
        minus.setLongClickable(true);
        repeatHandler = new Handler();

        Button Schedule = (Button) findViewById(R.id.Schedule);

        Schedule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent weekIntent = new Intent(view.getContext(), Weekoverview.class);
                startActivity(weekIntent);
            }
        });


        // this part sets the initial values of the target and current temperature
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    targetTemperature = Double.parseDouble(HeatingSystem.get("targetTemperature"));
                    currentTemperature = Double.parseDouble(HeatingSystem.get("currentTemperature"));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateTargetTempView();
                            updateCurrentTempView();
                        }
                    });

                } catch (ConnectException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String weekProgramState = HeatingSystem.get("weekProgramState");
                    if (weekProgramState.equals("off")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                holdButton.setChecked(true);
                            }
                        });
                    }
                } catch (ConnectException e) {
                    e.printStackTrace();
                }
                ;
            }
        }).start();


        repeatPlus = new Runnable() {
            @Override
            public void run() {
                System.out.println("Increased temp");
                targetTemperature = (targetTemperature * 10 + 1) / 10; // to prevent rounding issues
                updateTargetTempView();
                repeatHandler.postDelayed(repeatPlus, CLICK_INTERVAL);
            }
        };

        repeatMinus = new Runnable() {
            @Override
            public void run() {
                System.out.println("Decreased temp");
                targetTemperature = (targetTemperature * 10 - 1) / 10; // to prevent rounding issues
                updateTargetTempView();
                repeatHandler.postDelayed(repeatMinus, CLICK_INTERVAL);
            }
        };

        plus.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        repeatHandler.post(repeatPlus);
                        break;
                    case MotionEvent.ACTION_UP:
                        repeatHandler.removeCallbacks(repeatPlus);
                        sendTargetTempToServer(); // only updates to server once done increasing to save bandwidth, good idea?
                        break;
                }
                return true;
            }
        });

        minus.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        repeatHandler.post(repeatMinus);
                        break;
                    case MotionEvent.ACTION_UP:
                        repeatHandler.removeCallbacks(repeatMinus);
                        sendTargetTempToServer();
                        break;
                }
                return true;
            }
        });

        holdButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    setWeekProgramDisabled();
                } else {
                    setWeekProgramEnabled();
                }
            }
        });

        refreshTimer = new CountDownTimer(1000, 1000) {

            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                refreshCurrent();
            }

        }.start();
    }

    void setWeekProgramDisabled() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HeatingSystem.put("weekProgramState", "off");
                } catch (InvalidInputValueException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    void setWeekProgramEnabled() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HeatingSystem.put("weekProgramState", "on");
                    try {
                        targetTemperature = Double.parseDouble(HeatingSystem.get("targetTemperature"));
                    } catch (ConnectException e) {
                        e.printStackTrace();
                    }
                } catch (InvalidInputValueException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        System.out.println(targetTemperature);
        updateTargetTempView(); // does not update correctly, maybe thread is not finished
    }

    void refreshCurrent() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    currentTemperature = Double.parseDouble(HeatingSystem.get("currentTemperature"));
                    refreshTimer.start();
                } catch (ConnectException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateCurrentTempView();
            }
        });
    }

    void updateTargetTempView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tempTarget.setText(String.valueOf(targetTemperature) + "\u2103");
            }
        });
    }

    void updateCurrentTempView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tempCurrent.setText(String.valueOf(currentTemperature) + "\u2103");
            }
        });
    }

    void sendTargetTempToServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HeatingSystem.put("targetTemperature", String.valueOf(targetTemperature));
                } catch (InvalidInputValueException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }
}
