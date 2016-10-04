package com.eveningoutpost.dexdrip;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.UtilityModels.Constants;
import com.eveningoutpost.dexdrip.utils.ActivityWithMenu;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.util.ChartUtils;
import lecho.lib.hellocharts.view.LineChartView;


public class CalibrationGraph extends ActivityWithMenu {
    //public static String menu_name = "Calibration Graph";
    private LineChartView chart;
    private LineChartData data;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private final boolean doMgdl = Home.getPreferencesStringWithDefault("units", "mgdl").equals("mgdl");
    private final boolean show_days_since = true; // could make this switchable if desired
    private final double start_x = 50; // raw range
    private final double end_x = 300; //  raw range

    TextView GraphHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_graph);
        JoH.fixActionBar(this);
        GraphHeader = (TextView) findViewById(R.id.CalibrationGraphHeader);
    }

    @Override
    public String getMenuName() {
        return getString(R.string.calibration_graph);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupCharts();
    }

    public void setupCharts() {
        chart = (LineChartView) findViewById(R.id.chart);
        List<Line> lines = new ArrayList<Line>();

        Calibration calibration = Calibration.lastValid();
        if (calibration != null) {
            //set header
            DecimalFormat df = new DecimalFormat("#");
            df.setMaximumFractionDigits(2);
            df.setMinimumFractionDigits(2);
            String Header = "slope = " + df.format(calibration.slope) + " intercept = " + df.format(calibration.intercept);
            GraphHeader.setText(Header);

            //red line
            List<PointValue> lineValues = new ArrayList<PointValue>();
            final float conversion_factor = (float) (doMgdl ? 1 : Constants.MGDL_TO_MMOLL);

            lineValues.add(new PointValue((float) start_x, (conversion_factor * (float) (start_x * calibration.slope + calibration.intercept))));
            lineValues.add(new PointValue((float) end_x, (conversion_factor * (float) (end_x * calibration.slope + calibration.intercept))));
            Line calibrationLine = new Line(lineValues);
            calibrationLine.setColor(ChartUtils.COLOR_RED);
            calibrationLine.setHasLines(true);
            calibrationLine.setHasPoints(false);

            //calibration values
            List<Calibration> calibrations = Calibration.allForSensor();
            Line greyLine = getCalibrationsLine(calibrations, Color.parseColor("#66FFFFFF"));
            calibrations = Calibration.allForSensorInLastFourDays();
            Line blueLine = getCalibrationsLine(calibrations, ChartUtils.COLOR_BLUE);

            //add lines in order
            lines.add(greyLine);
            lines.add(blueLine);
            lines.add(calibrationLine);

        }
        Axis axisX = new Axis();
        Axis axisY = new Axis().setHasLines(true);
        axisX.setName("Raw Value");
        axisY.setName("Glucose " + (doMgdl ? "mg/dl" : "mmol/l"));


        data = new LineChartData(lines);
        data.setAxisXBottom(axisX);
        data.setAxisYLeft(axisY);
        chart.setLineChartData(data);

    }

    private static int daysAgo(long when) {
        return (int) (JoH.tsl() - when) / 86400000;
    }

    @NonNull
    public Line getCalibrationsLine(List<Calibration> calibrations, int color) {
        List<PointValue> values = new ArrayList<PointValue>();
        for (Calibration calibration : calibrations) {
            PointValue point = new PointValue((float) calibration.estimate_raw_at_time_of_calibration,
                    doMgdl ? (float) calibration.bg : ((float) calibration.bg) * (float) Constants.MGDL_TO_MMOLL);
            String time;
            if (show_days_since) {
                final int days_ago = daysAgo(calibration.raw_timestamp);
                time = (days_ago > 0) ? Integer.toString(days_ago) + "d  " : "";
                time = time + (JoH.hourMinuteString(calibration.raw_timestamp));
            } else {
                time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date((long) calibration.raw_timestamp));
            }
            point.setLabel(time);
            values.add(point);
        }

        Line line = new Line(values);
        line.setColor(color);
        line.setHasLines(false);
        line.setPointRadius(4);
        line.setHasPoints(true);
        line.setHasLabels(true);
        return line;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        //Just generate the menu in engineering mode
        if (!Home.getPreferencesBooleanDefaultFalse("engineering_mode")) {
            return false;
        }

        getMenuInflater().inflate(R.menu.menu_calibrationgraph, menu);

        // Only show elements if there is a calibration to overwrite
        if (Calibration.lastValid() != null) {
            menu.findItem(R.id.action_overwrite_intercept).setVisible(true);
            menu.findItem(R.id.action_overwrite_slope).setVisible(true);
            menu.findItem(R.id.action_overwrite_calibration_impossible).setVisible(false);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_overwrite_intercept:
                overWriteIntercept();
                return true;
            //break;
            case R.id.action_overwrite_slope:
                overWriteSlope();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void overWriteIntercept() {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        new AlertDialog.Builder(this)
                .setTitle("Ovewrite Intercept")
                .setMessage("Overwrite Intercept")
                .setView(editText)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String text = editText.getText().toString();
                        if (!TextUtils.isEmpty(text)) {
                            double doubleValue = JoH.tolerantParseDouble(text);
                            Calibration calibration = Calibration.lastValid();
                            calibration.intercept = doubleValue;
                            calibration.save();
                            recreate();
                        } else {
                            JoH.static_toast_long("Input not found! Cancelled!");
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        JoH.static_toast_long("Cancelled!");
                    }
                })
                .show();
    }

    private void overWriteSlope() {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        new AlertDialog.Builder(this)
                .setTitle("Ovewrite Slope")
                .setMessage("Overwrite Slope")
                .setView(editText)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String text = editText.getText().toString();
                        if (!TextUtils.isEmpty(text)) {
                            double doubleValue = JoH.tolerantParseDouble(text);
                            Calibration calibration = Calibration.lastValid();
                            calibration.slope = doubleValue;
                            calibration.save();
                            recreate();
                        } else {
                            JoH.static_toast_long("Input not found! Cancelled!");
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        JoH.static_toast_long("Cancelled!");
                    }
                })
                .show();
    }


}