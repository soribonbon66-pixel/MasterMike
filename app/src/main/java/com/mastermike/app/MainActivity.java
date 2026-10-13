package com.mastermike.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Initial screen for MasterMike.
 *
 * <p>The project intentionally uses only Android platform views, so the first APK can be
 * compiled without adding a third-party dependency.</p>
 */
public class MainActivity extends Activity {

    private int color(int resourceId) {
        return getResources().getColor(resourceId);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, float sizeSp, int textColor) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable roundedBackground(int fillColor, float radiusDp, int strokeColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fillColor);
        background.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            background.setStroke(dp(1), strokeColor);
        }
        return background;
    }

    private View spacer(int heightDp) {
        Space spacer = new Space(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp)
        ));
        return spacer;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int backgroundColor = color(R.color.background);
        getWindow().setStatusBarColor(backgroundColor);
        getWindow().setNavigationBarColor(backgroundColor);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(backgroundColor);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(40), dp(24), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.MATCH_PARENT
        ));

        TextView mark = label("M", 28, color(R.color.background));
        mark.setGravity(Gravity.CENTER);
        mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mark.setBackground(roundedBackground(color(R.color.accent), 18, 0));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(64), dp(64));
        content.addView(mark, markParams);

        content.addView(spacer(22));

        TextView tagline = label(getString(R.string.app_tagline).toUpperCase(), 12,
                color(R.color.accent));
        tagline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tagline.setLetterSpacing(0.16f);
        content.addView(tagline, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        content.addView(spacer(14));

        TextView title = label(getString(R.string.welcome_title), 30, color(R.color.text_primary));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        content.addView(spacer(14));

        TextView message = label(getString(R.string.welcome_message), 16,
                color(R.color.text_secondary));
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(0, 1.2f);
        content.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        content.addView(spacer(28));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundedBackground(
                color(R.color.surface), 20, color(R.color.surface_border)));
        content.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView cardTitle = label("Proyecto preparado", 15, color(R.color.text_primary));
        cardTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(cardTitle);

        TextView cardDescription = label(
                "Compila una APK de prueba con el botón de abajo.",
                14,
                color(R.color.text_secondary)
        );
        cardDescription.setPadding(0, dp(8), 0, 0);
        card.addView(cardDescription);

        content.addView(spacer(22));

        Button startButton = new Button(this);
        startButton.setText(R.string.start_button);
        startButton.setTextColor(color(R.color.background));
        startButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        startButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        startButton.setAllCaps(false);
        startButton.setStateListAnimator(null);
        startButton.setBackground(roundedBackground(color(R.color.accent), 16, 0));
        startButton.setPadding(dp(18), 0, dp(18), 0);
        startButton.setOnClickListener(view -> Toast.makeText(
                this,
                R.string.start_message,
                Toast.LENGTH_SHORT
        ).show());
        content.addView(startButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        ));

        content.addView(spacer(22));

        TextView version = label(getString(R.string.build_label), 12, color(R.color.text_secondary));
        version.setGravity(Gravity.CENTER);
        content.addView(version, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        setContentView(scrollView);
    }
}
